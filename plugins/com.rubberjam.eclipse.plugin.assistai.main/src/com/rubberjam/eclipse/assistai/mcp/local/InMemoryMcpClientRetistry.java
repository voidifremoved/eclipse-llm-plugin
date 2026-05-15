
package com.rubberjam.eclipse.assistai.mcp.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.e4.ui.workbench.lifecycle.PostWorkbenchClose;

import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;
import com.rubberjam.eclipse.assistai.mcp.local.InMemoryClientServerFactory.InMemorySyncClientServer;
import com.rubberjam.eclipse.assistai.mcp.remote.RemoteMcpClientFactory;
import com.rubberjam.eclipse.assistai.tools.EclipseVariableUtilities;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.json.schema.jackson2.JacksonJsonSchemaValidatorSupplier;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpClientTransport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;


@Creatable
@Singleton
public class InMemoryMcpClientRetistry
{
    private Map<String, McpSyncClient> clients = new HashMap<>();

    private List<McpSyncServer>        servers = new ArrayList<>();

    private boolean                    initialized;

    @Inject
    private ILog                       logger;

    @Inject
    private InMemoryClientServerFactory     factory;

    @Inject
    private McpServerRepository        mcpServerRepository;

    /**
     * Handles the shutdown process by closing all MCP clients gracefully.
     */
    @PostWorkbenchClose
    public void handleShutdown()
    {
        clients.values().forEach( McpSyncClient::closeGracefully );
        servers.forEach( McpSyncServer::closeGracefully );
    }

    /**
     * Loads enabled MCP clients on first use. Deferred from {@code @PostConstruct} so E4 can
     * construct {@link com.rubberjam.eclipse.assistai.agent.McpToolBridge} and related beans first.
     */
    public void ensureInitialized()
    {
        if ( initialized )
        {
            return;
        }
        synchronized ( this )
        {
            if ( initialized )
            {
                return;
            }
            loadClients();
            initialized = true;
        }
    }

    private void loadClients()
    {
        var stored = mcpServerRepository.listStoredServers();
        var builtin = mcpServerRepository.listBuiltInServers();

        initializeBuiltInServers( stored, builtin );
        initializeUserDefinedServers( stored );

        for ( Map.Entry<String, McpSyncClient> client : clients.entrySet() )
        {
            gracefullyInitialize( client );
        }
    }
    
    private void gracefullyInitialize( Map.Entry<String, McpSyncClient> client )
    {
        try
        {
            logger.info( "Initializing MCP client: " + client.getKey()  );
            CompletableFuture.supplyAsync( () -> client.getValue().initialize() )
                             .get( 3, TimeUnit.SECONDS );
            logger.info( "Sucessfully initialized MCP client: " + client.getKey()  );
        }
        catch ( InterruptedException | ExecutionException | TimeoutException e )
        {
            String errorStackTrace = "";
            try (ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
                 PrintStream errorStream = new PrintStream( errorOut ) ) 
            {
                
                e.printStackTrace(errorStream);
                errorStackTrace += errorOut.toString();
            }
            catch ( IOException ignore )
            {
            }
            logger.error( "Failed to initialize MCP client: " + client.getKey() + ". Exception: \n" + errorStackTrace );
        }
    }
    
    /**
     * Initializes built-in MCP servers.
     *
     * @param stored
     *            List of stored server descriptors.
     * @param builtin
     *            List of built-in server descriptors.
     */
    private void initializeBuiltInServers( List<McpServerDescriptor> stored, List<McpServerDescriptor> builtin )
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

                InMemorySyncClientServer  clientServerPair = factory.creteInMemorySyncClientServerPair( implementation, updated.excludedTools() );
                addClient( updated.name(), clientServerPair.client() );
                servers.add( clientServerPair.server() );
            }
        }
    }

    /**
     * Initializes user-defined MCP servers.
     *
     * @param stored
     *            List of stored server descriptors.
     */

    private void initializeUserDefinedServers(List<McpServerDescriptor> stored) {
        var userDefined = stored.stream()
                                .filter(Predicates.not(McpServerDescriptor::builtIn))
                                .filter(McpServerDescriptor::enabled)
                                .collect(Collectors.toList());
    
        for (var userMcp : userDefined)
        {
            Map<String, String> resolvedEnvVars = userMcp.environmentVariables().stream()
                    .collect( Collectors.toMap(
                            McpServerDescriptor.EnvironmentVariable::name,
                            ev -> EclipseVariableUtilities.resolveEclipseVariables( ev.value() ) ) );

            McpSyncClient client;
            if ( userMcp.isHttpServer() )
            {
                String resolvedUrl = EclipseVariableUtilities.resolveEclipseVariables( userMcp.url() );
                client = RemoteMcpClientFactory.createHttpClient( resolvedUrl, resolvedEnvVars );
            }
            else
            {
                String resolvedCommand = EclipseVariableUtilities.resolveEclipseVariables( userMcp.command() );
                var commandParts = parseCommand( resolvedCommand );
                String executable = commandParts.get( 0 );
                String[] args = commandParts.subList( 1, commandParts.size() ).toArray( new String[0] );
                ServerParameters stdioParameters = ServerParameters.builder( executable )
                        .args( args )
                        .env( resolvedEnvVars )
                        .build();
                JacksonMcpJsonMapperSupplier jsonMapperSupplier = new JacksonMcpJsonMapperSupplier();
                McpClientTransport mcpTransport = new StdioClientTransport( stdioParameters, jsonMapperSupplier.get() );
                client = McpClient.sync( mcpTransport )
                        .jsonSchemaValidator( new JacksonJsonSchemaValidatorSupplier().get() )
                        .build();
            }
            addClient( userMcp.name(), client );
        }
    }

    

    /**
	 * Parses a command string into a list of command parts.
	 *
	 * @param command
	 *            The command string to parse.
	 * @return A list of command parts.
	 */
	private static List<String> parseCommand( String command )
	{
	    List<String> commandParts = new ArrayList<>();
	    Matcher matcher = Pattern.compile( "([^\"]\\S*|\".+?\")\\s*" ).matcher( command );
	    while ( matcher.find() )
	    {
	        commandParts.add( matcher.group( 1 ).replace( "\"", "" ) );
	    }
	    return commandParts;
	}



    /**
     * Adds a client to the registry.
     *
     * @param name
     *            The name of the client.
     * @param client
     *            The MCP sync client to add.
     */
    public void addClient( String name, McpSyncClient client )
    {
        clients.put( name, client );
    }

    /**
     * Lists all registered MCP clients.
     *
     * @return A map of client names to MCP sync clients.
     */
    public Map<String, McpSyncClient> listClients()
    {
        ensureInitialized();
        return clients;
    }
    
    public Map<String, McpSyncClient> listEnabledClients()
    {
        ensureInitialized();
    	// map server name to its enabled status
    	Map<String, Boolean> enabled = mcpServerRepository.listStoredServers().stream()
    													  .collect( Collectors.toMap(McpServerDescriptor::name, McpServerDescriptor::enabled));
    	// return only enabled
    	return clients.entrySet().stream()
    				  			 .filter( e -> enabled.getOrDefault(e.getKey(), Boolean.FALSE ).booleanValue() )
    				  			 .collect(Collectors.toMap( Map.Entry::getKey, Map.Entry::getValue) );
    }

    /**
     * Finds a tool by client name.
     *
     * @param clientName
     *            The name of the client.
     * @return An optional containing the MCP sync client if found.
     */
    public Optional<McpSyncClient> findClient( String clientName )
    {
        ensureInitialized();
        return Optional.ofNullable( clients.get( clientName ) );
    }

    public void restart()
    {
        handleShutdown();
        clients.clear();
        servers.clear();
        initialized = false;
        ensureInitialized();
    }

    
}
