package com.rubberjam.eclipse.assistai.mcp.http;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;

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

@Creatable
@Singleton
public class HttpMcpServerRegistry
{
    
    private static String MCP_ENDPOINT = "/mcp";
    
    private final HttpMcpServerPreferencesProvider httpServerPreferncesProvider;
    private final McpServerRepository mcpServerRepository;
    private final McpServerFactory mcpServerFactory;
    private final ILog logger;
    
    private final List<McpSyncServer> servers;
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
        this.endpoints = new ArrayList<>();
        this.jsonMapperSupplier = new JacksonMcpJsonMapperSupplier();

    }
    
    /**
     * Handles the shutdown process by closing all MCP clients gracefully.
     */
    @PostWorkbenchClose
    public void handleShutdown()
    {
        servers.forEach( McpSyncServer::closeGracefully );
        if ( tomcat != null )
        {
            try
            {
                tomcat.stop();
                tomcat.destroy();
            }
            catch ( LifecycleException e )
            {
                logger.error( "Tomcat server failed to stop: " + e.getMessage(), e );
            }
            tomcat = null;
        }
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
    
    public List<String> listEndpoints()
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

    public boolean isRunning()
    {
        return tomcat != null && LifecycleState.STARTED.equals( tomcat.getServer().getState() );
    }

    public void restart()
    {
        // Full teardown
        servers.forEach( McpSyncServer::closeGracefully );
        servers.clear();
        endpoints.clear();

        if ( tomcat != null )
        {
            try
            {
                logger.info( "Stopping MCP Http Server." );
                tomcat.stop();
                tomcat.destroy();
            }
            catch ( LifecycleException e )
            {
                logger.error( "Error stopping Tomcat server: " + e.getMessage(), e );
            }
            tomcat = null;
        }

        if ( !httpServerPreferncesProvider.isEnabled() )
        {
            return;
        }

        // Full rebuild
        try
        {
            tomcat = createTomcatServer();
            String baseDir = System.getProperty( "java.io.tmpdir" );
            Context context = tomcat.addContext( "", baseDir );

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
            logger.info( "MCP Http Server state: " + tomcat.getServer().getState() + " @" + tomcat.getServer().getAddress() + ":" + tomcat.getServer().getPort() );
            logger.info( "MCP Http Server endpoints:\n " + listEndpoints().stream().collect( Collectors.joining( "\n" ) ) );
        }
        catch ( LifecycleException e )
        {
            logger.error( "Error starting MCP Http Server: " + e.getMessage(), e );
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
