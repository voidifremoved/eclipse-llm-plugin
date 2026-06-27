package com.rubberjam.eclipse.assistai.mcp.http;


import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.ValveBase;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.e4.ui.workbench.lifecycle.PostWorkbenchClose;

import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerFactory;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;

@Creatable
@Singleton
public class HttpMcpServerRegistry
{
    
    private static String MCP_ENDPOINT = "/mcp";

    private static final Duration TRANSPORT_SHUTDOWN_TIMEOUT = Duration.ofSeconds( 10 );
    
    private final HttpMcpServerPreferencesProvider httpServerPreferncesProvider;
    private final McpServerRepository mcpServerRepository;
    private final McpServerFactory mcpServerFactory;
    private final ILog logger;
    
    private final List<McpSyncServer> servers;
    private final List<HttpServletStreamableServerTransportProvider> transportProviders;
    private final ArrayList<String> endpoints;

    private Tomcat tomcat;

    private JacksonMcpJsonMapperSupplier jsonMapperSupplier;

    
    @Inject
    public HttpMcpServerRegistry( HttpMcpServerPreferencesProvider serverPreferncesProvider,
                                  McpServerRepository mcpServerRepository, 
                                  McpServerFactory mcpServerFactory, 
                                  ILog logger )
    {
        Objects.requireNonNull( serverPreferncesProvider );
        Objects.requireNonNull( mcpServerRepository );
        Objects.requireNonNull( mcpServerFactory );
        Objects.requireNonNull( logger );
        this.httpServerPreferncesProvider = serverPreferncesProvider;
        this.mcpServerFactory = mcpServerFactory;
        this.mcpServerRepository = mcpServerRepository;
        this.logger = logger;
        
        this.servers = new ArrayList<>();
        this.transportProviders = new ArrayList<>();
        this.endpoints = new ArrayList<>();
        this.jsonMapperSupplier = new JacksonMcpJsonMapperSupplier();

    }
    
    /**
     * Handles the shutdown process by closing all MCP clients gracefully.
     */
    @PostWorkbenchClose
    public synchronized void handleShutdown()
    {
        stopTomcatServer();
        closeMcpServersAndTransports();
    }

    @PostConstruct
    public void init()
    {
        logger.info( "Initializing MCP Http Server." );
        restart();
    }
    
    private void initializeBuiltInServers(Context context, List<McpServerDescriptor> stored, List<McpServerDescriptor> builtin )
    {
        for ( McpServerDescriptor builtInServerDescriptor : builtin )
        {
            McpServerDescriptor updated = stored.stream()
                                                .filter( other -> builtInServerDescriptor.uid().equals( other.uid() ) )
                                                .findAny()
                                                .orElse( builtInServerDescriptor );
    
            if ( updated.enabled() )
            {
                var implementation = mcpServerRepository.makeImplementation( updated.name() );
                var transportProvider = createStreamableHttpTransportProvider( updated.name() );
                var server = mcpServerFactory.createSyncServer( implementation, transportProvider, updated.excludedTools() );
                transportProviders.add( transportProvider );
                servers.add( server );
                
                // Wrap the transportProvider in a servlet that ensures the correct thread context classloader is active on Tomcat threads
                Servlet wrappedServlet = new ClassLoaderWrapperServlet( transportProvider, HttpMcpServerRegistry.class.getClassLoader() );
                addServlet(context, updated.name(), wrappedServlet);  // Pass context and name
            }
        }
    }
    
    private void addServlet(Context context, String serverName, Servlet servlet)
    {
        // Add transport servlet to the shared context
        var wrapper = context.createWrapper();
        wrapper.setName("mcpServlet_" + serverName);  // Unique name per server
        wrapper.setServlet(servlet);
        wrapper.setLoadOnStartup(1);
        wrapper.setAsyncSupported(true);
        context.addChild(wrapper);
        context.addServletMappingDecoded(MCP_ENDPOINT + "/" + serverName + "/*", "mcpServlet_" + serverName);

        // Track the endpoint
        endpoints.add(serverName);
    }

    private HttpServletStreamableServerTransportProvider createStreamableHttpTransportProvider( String name )
    {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapperSupplier.get())
                .keepAliveInterval(java.time.Duration.ofSeconds(10))
                .mcpEndpoint(MCP_ENDPOINT + "/" + name )
                .build();
    }
    
    public synchronized List<String> listEndpoints()
    {
        var config = httpServerPreferncesProvider.get();
        String baseUrl = "http://" + config.hostname() + ":" + config.port();
        
        return endpoints.stream()
                .map(name -> baseUrl + MCP_ENDPOINT + "/"  + name)
                .toList();
    }

    
    private Tomcat createTomcatServer()
    {
        // Disable Tomcat's URL stream handler factory to avoid conflicts with OSGi
        System.setProperty("tomcat.util.buf.StringCache.byte.enabled", "true");
        org.apache.catalina.webresources.TomcatURLStreamHandlerFactory.disable();
        var tomcat = new Tomcat();
        tomcat.setPort(httpServerPreferncesProvider.get().port());
        tomcat.setHostname(httpServerPreferncesProvider.get().hostname());
        
        String baseDir = System.getProperty("java.io.tmpdir");
        tomcat.setBaseDir(baseDir);

        var connector = tomcat.getConnector();
        connector.setAsyncTimeout(1800000); // 30 minutes to prevent SSE streams from timing out too early

        return tomcat;
    }

    public synchronized boolean isRunning()
    {
        return tomcat != null && LifecycleState.STARTED.equals( tomcat.getServer().getState() );
    }

    public synchronized void restart()
    {
        stopTomcatServer();
        closeMcpServersAndTransports();

        if ( !httpServerPreferncesProvider.isEnabled() )
        {
            logger.info( "MCP Http Server is disabled; not starting." );
            return;
        }

        // Tomcat spawns its acceptor/poller/worker threads during start(); those threads
        // inherit the thread-context classloader of the calling thread (here the Eclipse UI
        // thread). Inside Equinox that classloader cannot drive Tomcat's request machinery,
        // which leaves connections accepted but never answered. Pin the TCCL to this bundle's
        // classloader for the whole start so the worker threads inherit a usable loader.
        Thread currentThread = Thread.currentThread();
        ClassLoader previousTccl = currentThread.getContextClassLoader();
        ClassLoader pluginClassLoader = HttpMcpServerRegistry.class.getClassLoader();
        currentThread.setContextClassLoader( pluginClassLoader );

        logger.info( "MCP Http Server: starting with thread-context classloader " + pluginClassLoader
                + " (previous=" + previousTccl + ")" );

        try
        {
            tomcat = createTomcatServer();
            String baseDir = System.getProperty( "java.io.tmpdir" );
            Context context = tomcat.addContext( "", baseDir );

            // First valve in the pipeline: logs every request that reaches the context so we
            // can tell whether requests are arriving at all, and on which classloader.
            context.getPipeline().addValve( new RequestLoggingValve( logger ) );

            var builtin = mcpServerRepository.listBuiltInServers();
            var stored = mcpServerRepository.listStoredServers();
            initializeBuiltInServers( context, stored, builtin );

            String token = httpServerPreferncesProvider.get().token();
            if ( token != null && !token.isBlank() )
            {
                context.getPipeline().addValve( new BearerTokenAuthenticationValve( token ) );

                logger.info( "MCP Http Server: Bearer token authentication enabled." );
            }
            else
            {
                logger.warn( "MCP Http Server: No authentication token configured - server is unprotected!" );
            }

            logger.info( "Starting MCP Http Server." );
            tomcat.start();
            logger.info( "MCP Http Server state: " + tomcat.getServer().getState()
                    + " connector=" + describeConnector() );
            logger.info( "MCP Http Server endpoints:\n " + listEndpoints().stream().collect( Collectors.joining( "\n" ) ) );
        }
        catch ( LifecycleException e )
        {
            logger.error( "Error starting MCP Http Server: " + e.getMessage(), e );
            stopTomcatServer();
            closeMcpServersAndTransports();
        }
        finally
        {
            currentThread.setContextClassLoader( previousTccl );
        }
    }

    private String describeConnector()
    {
        if ( tomcat == null )
        {
            return "<none>";
        }
        Connector connector = tomcat.getConnector();
        if ( connector == null )
        {
            return "<none>";
        }
        return connector.getScheme() + "://" + httpServerPreferncesProvider.get().hostname() + ":" + connector.getPort()
                + " state=" + connector.getState();
    }

    private void stopTomcatServer()
    {
        if ( tomcat == null )
        {
            return;
        }

        Tomcat stoppingTomcat = tomcat;
        tomcat = null;

        try
        {
            logger.info( "Stopping MCP Http Server." );
            Connector connector = stoppingTomcat.getConnector();
            if ( connector != null )
            {
                connector.pause();
                connector.stop();
            }
            stoppingTomcat.stop();
        }
        catch ( LifecycleException e )
        {
            logger.error( "Error stopping Tomcat server: " + e.getMessage(), e );
        }
        finally
        {
            try
            {
                stoppingTomcat.destroy();
            }
            catch ( LifecycleException e )
            {
                logger.error( "Error destroying Tomcat server: " + e.getMessage(), e );
            }
        }
    }

    private void closeMcpServersAndTransports()
    {
        for ( McpSyncServer server : servers )
        {
            try
            {
                server.closeGracefully();
            }
            catch ( RuntimeException e )
            {
                logger.error( "Error closing MCP server: " + e.getMessage(), e );
            }
        }
        servers.clear();

        for ( HttpServletStreamableServerTransportProvider transportProvider : transportProviders )
        {
            try
            {
                transportProvider.closeGracefully().block( TRANSPORT_SHUTDOWN_TIMEOUT );
            }
            catch ( RuntimeException e )
            {
                logger.error( "Error closing MCP HTTP transport provider: " + e.getMessage(), e );
            }
        }
        transportProviders.clear();
        endpoints.clear();
    }
    
    /**
     * Diagnostic valve placed first in the context pipeline. It records every request that
     * reaches the context, the classloader of the processing thread, and the resulting status
     * and duration so that connector/classloader stalls can be told apart from servlet errors.
     */
    private static class RequestLoggingValve extends ValveBase
    {
        private final ILog logger;

        RequestLoggingValve( ILog logger )
        {
            super( true );
            this.logger = logger;
        }

        @Override
        public void invoke( Request request, Response response ) throws IOException, ServletException
        {
            long start = System.currentTimeMillis();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            try
            {
                getNext().invoke( request, response );
            }
            catch ( IOException | ServletException | RuntimeException | Error e )
            {
                logger.error( "[MCP Http] " + method + " " + uri + " failed: " + e, e );
                throw e;
            }
            finally
            {
                logger.info( "[MCP Http] " + method + " " + uri + " -> " + response.getStatus()
                        + " (" + ( System.currentTimeMillis() - start ) + "ms)" );
            }
        }
    }

    private static class ClassLoaderWrapperServlet implements Servlet
    {
        private final Servlet delegate;
        private final ClassLoader classLoader;

        public ClassLoaderWrapperServlet( Servlet delegate, ClassLoader classLoader )
        {
            this.delegate = delegate;
            this.classLoader = classLoader;
        }

        @Override
        public void init( jakarta.servlet.ServletConfig config ) throws jakarta.servlet.ServletException
        {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader( classLoader );
            try
            {
                delegate.init( config );
            }
            finally
            {
                Thread.currentThread().setContextClassLoader( old );
            }
        }

        @Override
        public jakarta.servlet.ServletConfig getServletConfig()
        {
            return delegate.getServletConfig();
        }

        @Override
        public void service( jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res )
                throws jakarta.servlet.ServletException, java.io.IOException
        {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader( classLoader );
            try
            {
                delegate.service( req, res );
            }
            finally
            {
                Thread.currentThread().setContextClassLoader( old );
            }
        }

        @Override
        public String getServletInfo()
        {
            return delegate.getServletInfo();
        }

        @Override
        public void destroy()
        {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader( classLoader );
            try
            {
                delegate.destroy();
            }
            finally
            {
                Thread.currentThread().setContextClassLoader( old );
            }
        }
    }
}
