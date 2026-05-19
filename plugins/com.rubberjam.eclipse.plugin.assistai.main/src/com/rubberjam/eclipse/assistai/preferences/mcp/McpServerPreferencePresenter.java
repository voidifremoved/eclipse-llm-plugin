package com.rubberjam.eclipse.assistai.preferences.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor.EnvironmentVariable;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor.McpServerDescriptorWithStatus;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor.Status;
import com.rubberjam.eclipse.assistai.mcp.http.HttpMcpServerRegistry;
import com.rubberjam.eclipse.assistai.agent.AgentSessionManager;
import com.rubberjam.eclipse.assistai.agent.AgentToolPolicy;
import com.rubberjam.eclipse.assistai.mcp.local.InMemoryMcpClientRegistry;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;
import com.rubberjam.eclipse.assistai.mcp.remote.RemoteMcpClientFactory;

import io.modelcontextprotocol.spec.McpSchema;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Presenter for MCP Server preferences
 */
@Creatable
@Singleton
public class McpServerPreferencePresenter
{

    private static final int MCP_SERVER_PING_TIMEOUT_SECONDS = 1;
    private static final int MCP_TOOL_DISCOVERY_TIMEOUT_SECONDS = 20;

    private final InMemoryMcpClientRegistry clientRetistry;
    private final HttpMcpServerRegistry httpMcpServerRegistry;
    private final McpServerRepository mcpServerRepository;
    private final ILog logger;

    private final AgentToolPolicy agentToolPolicy;
    
    private McpServerPreferencePage view;

    private String lastDiscoveredUrl = "";

    @Inject
    public McpServerPreferencePresenter( InMemoryMcpClientRegistry mcpClientRetistry,
                                         HttpMcpServerRegistry httpMcpServerRegistry,
                                         McpServerRepository mcpServerRepository,
                                         AgentToolPolicy agentToolPolicy,
                                         ILog logger
                                         )
    {
        Objects.requireNonNull( mcpClientRetistry );
        Objects.requireNonNull( httpMcpServerRegistry );
        Objects.requireNonNull( mcpServerRepository );
        Objects.requireNonNull( agentToolPolicy );
        Objects.requireNonNull( logger );
        
        this.clientRetistry = mcpClientRetistry;
        this.httpMcpServerRegistry = httpMcpServerRegistry;
        this.mcpServerRepository = mcpServerRepository;
        this.agentToolPolicy = agentToolPolicy;
        this.logger = logger;
    }

    public boolean isAllowWebTools()
    {
        return agentToolPolicy.isAllowWebTools();
    }

    public boolean isUseEclipseSkillsInPrompt()
    {
        return agentToolPolicy.isUseEclipseSkillsInPrompt();
    }

    public void saveAgentToolPreferences( boolean allowWebTools, boolean useEclipseSkills )
    {
        agentToolPolicy.setAllowWebTools( allowWebTools );
        agentToolPolicy.setUseEclipseSkillsInPrompt( useEclipseSkills );
        Activator.getDefault().make( AgentSessionManager.class ).refreshMcpToolsOnAllSessions();
    }

    public void applyWorkspaceAgentPreset()
    {
        agentToolPolicy.applyWorkspaceAgentPreset();
        restartServers();
        if ( view != null )
        {
            view.showServers( getServersWithStatus() );
            view.syncAgentPolicyControls();
        }
    }
    
    /**
     * Get all defined MCP servers
     * 
     * @return list of MCP server descriptors
     */
    public List<McpServerDescriptorWithStatus> getServersWithStatus()
    {
        var servers = mcpServerRepository.listStoredServers();
        var clients = clientRetistry.listClients();
        var list = new ArrayList<McpServerDescriptorWithStatus>();
        for ( McpServerDescriptor server : servers )
        {
            if ( !server.enabled() )
            {
                list.add( new McpServerDescriptorWithStatus( server, Status.DISABLED ) );
                continue;
            }
            try
            {
                var client = clients.get( server.name() );
                if ( client == null )
                {
                    logger.error( "No MCP client for enabled server: " + server.name() );
                    list.add( new McpServerDescriptorWithStatus( server, Status.FAILED ) );
                    continue;
                }
                var result = CompletableFuture.supplyAsync( client::ping )
                        .get( MCP_SERVER_PING_TIMEOUT_SECONDS, TimeUnit.SECONDS );
                if ( result == null )
                {
                    list.add( new McpServerDescriptorWithStatus( server, Status.FAILED ) );
                }
                else
                {
                    list.add( new McpServerDescriptorWithStatus( server, Status.RUNNING ) );
                }
            }
            catch ( TimeoutException e )
            {
                logger.error( "Ping to MCP server timed out: " + server.name() );
                list.add( new McpServerDescriptorWithStatus( server, Status.FAILED ) );
            }
            catch ( Exception e )
            {
                logger.error( "Failed to connect to MCP server: " + server.name() + ": " + e.getMessage() );
                list.add( new McpServerDescriptorWithStatus( server, Status.FAILED ) );
            }
        }
        return list;
    }

    /**
     * Get a specific MCP server by index
     * 
     * @param index
     *            the index of the server
     * @return optional containing the server or empty if not found
     */
    public Optional<McpServerDescriptor> getServerAt( int index )
    {
        var servers = mcpServerRepository.listStoredServers();
        return index >= 0 && index < servers.size() ? Optional.of( servers.get( index ) ) : Optional.empty();
    }

    /**
     * Add a new MCP server
     */
    public void addServer()
    {
        lastDiscoveredUrl = "";
        view.prepareAddServer();
    }

    /**
     * Contacts an HTTP MCP endpoint and populates the tools list (called when the URL field loses focus).
     */
    public void discoverToolsFromUrl( String url, List<EnvironmentVariable> environmentVariables )
    {
        if ( url == null || url.isBlank() )
        {
            return;
        }
        String trimmedUrl = url.trim();
        if ( trimmedUrl.equals( lastDiscoveredUrl ) )
        {
            return;
        }

        view.setToolsDiscoveryInProgress( true );
        Map<String, String> headers = toHeaderMap( environmentVariables );

        CompletableFuture.supplyAsync( () -> RemoteMcpClientFactory.discoverToolNames( trimmedUrl, headers ) )
                .orTimeout( MCP_TOOL_DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS )
                .whenComplete( ( toolNames, error ) -> {
                    view.setToolsDiscoveryInProgress( false );
                    if ( error != null )
                    {
                        lastDiscoveredUrl = "";
                        String message = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
                        view.showError( "Failed to discover tools at " + trimmedUrl + ": " + message );
                        view.showToolList( Collections.emptyList(), Collections.emptyList() );
                        return;
                    }
                    lastDiscoveredUrl = trimmedUrl;
                    view.showToolList( toolNames, Collections.emptyList() );
                } );
    }

    /**
     * Toggle the enabled state of a server
     * 
     * @param serverIndex
     *            the index of the server
     * @param enabled
     *            the new enabled state
     */
    public void toggleServerEnabled( int serverIndex, boolean enabled )
    {
        List<McpServerDescriptor> servers = mcpServerRepository.listStoredServers();
        if ( serverIndex >= 0 && serverIndex < servers.size() )
        {
            McpServerDescriptor server = servers.get( serverIndex );
            McpServerDescriptor updated = new McpServerDescriptor( server.uid(), 
                                                                   server.name(), 
                                                                   server.command(),
                                                                   server.environmentVariables(), 
                                                                   enabled, 
                                                                   server.builtIn(),
                                                                   server.excludedTools(),
                                                                   server.url() );
            mcpServerRepository.upsertStoredServer( updated );
            restartServers();
        }
    }

    /**
     * Remove a server
     * 
     * @param selectedIndex
     *            the index of the server to remove
     */
    public void removeServer( int selectedIndex )
    {
        List<McpServerDescriptor> servers = mcpServerRepository.listStoredServers();
        if ( selectedIndex >= 0 && selectedIndex < servers.size() )
        {
            McpServerDescriptor server = servers.get( selectedIndex );
            if ( !server.builtIn() )
            {
                mcpServerRepository.removeStoredServerByUid( server.uid() );
                restartServers();
                view.showServers( getServersWithStatus() );
                view.clearServerDetails();
                view.setDetailsEditable( false );
                view.setAddingNewServer( false );
            }
        }
    }



    /**
     * Save a server from the details form.
     *
     * @param isNewServer
     *            {@code true} when the user clicked Add (must not reuse table selection)
     * @param displayIndex
     *            index in the merged server table when editing an existing row
     * @param updatedServerStub
     *            form data
     */
    /**
     * @return {@code false} if validation failed and nothing was saved
     */
    public boolean saveServer( boolean isNewServer, int displayIndex, McpServerDescriptor updatedServerStub )
    {
        McpServerPreferencesLog.info( "saveServer: isNewServer=" + isNewServer
                + " displayIndex=" + displayIndex
                + " addingNewServer=" + view.isAddingNewServer()
                + " stub=" + McpServerPreferencesLog.describe( updatedServerStub )
                + " repository@" + System.identityHashCode( mcpServerRepository ) );

        List<McpServerDescriptor> displayServers = mcpServerRepository.listStoredServers();

        String uid;
        boolean builtIn = false;
        if ( !isNewServer && displayIndex >= 0 && displayIndex < displayServers.size() )
        {
            McpServerDescriptor current = displayServers.get( displayIndex );
            if ( current.builtIn() )
            {
                McpServerPreferencesLog.warn( "saveServer: blocked update of built-in row index "
                        + displayIndex + " name=" + current.name() );
                view.showError( "Built-in servers cannot be replaced. Click Add to create a new MCP server." );
                return false;
            }
            uid = current.uid();
            builtIn = current.builtIn();
        }
        else
        {
            uid = UUID.randomUUID().toString();
            McpServerPreferencesLog.info( "saveServer: allocating new uid=" + uid );
        }

        final String uidToReplace = uid;
        String trimmedName = updatedServerStub.name() != null ? updatedServerStub.name().trim() : "";
        boolean nameExists = false;
        for ( McpServerDescriptor server : displayServers )
        {
            if ( !uidToReplace.equals( server.uid() ) && server.name().equals( trimmedName ) )
            {
                nameExists = true;
                break;
            }
        }

        if ( nameExists )
        {
            McpServerPreferencesLog.warn( "saveServer: duplicate name '" + trimmedName + "'" );
            view.showError( "Server name must be unique" );
            return false;
        }
        McpServerDescriptor toStore = new McpServerDescriptor( uid,
                trimmedName,
                updatedServerStub.command(),
                updatedServerStub.environmentVariables(),
                updatedServerStub.enabled(),
                builtIn,
                updatedServerStub.excludedTools(),
                updatedServerStub.url() );

        mcpServerRepository.upsertStoredServer( toStore );
        List<McpServerDescriptor> afterRaw = mcpServerRepository.listRawStoredServers();
        McpServerPreferencesLog.logDescriptors( "saveServer: raw after upsert", afterRaw );
        restartServers();
        List<McpServerDescriptorWithStatus> refreshed = getServersWithStatus();
        McpServerPreferencesLog.logDescriptorsWithStatus( "saveServer: UI refresh", refreshed );
        view.showServers( refreshed );
        view.clearServerDetails();
        view.setDetailsEditable( false );
        view.setAddingNewServer( false );
        return true;
    }

    /**
     * Set the selected server
     * 
     * @param selectedIndex
     *            the index of the server to select
     */
    public void setSelectedServer( int selectedIndex )
    {
        var servers = mcpServerRepository.listStoredServers();
        if ( selectedIndex >= 0 && selectedIndex < servers.size() )
        {
            view.setAddingNewServer( false );
            var selected = servers.get( selectedIndex );
            lastDiscoveredUrl = selected.url() != null ? selected.url().trim() : "";
            view.showServerDetails( selected );
            view.setDetailsEditable( !selected.builtIn() );
            view.setRemoveEditable( !selected.builtIn() );
            List<String> allTools = listToolsForDescriptor( selected );
            view.showToolList( allTools, selected.excludedTools() );
        }
        else if ( !view.isAddingNewServer() )
        {
            lastDiscoveredUrl = "";
            view.clearServerDetails();
            view.setDetailsEditable( false );
        }
    }

    private List<String> listToolsForDescriptor( McpServerDescriptor descriptor )
    {
        if ( descriptor.builtIn() )
        {
            return mcpServerRepository.listToolsForServer( descriptor.name() );
        }
        Optional<io.modelcontextprotocol.client.McpSyncClient> client = clientRetistry.findClient( descriptor.name() );
        if ( client.isPresent() )
        {
            try
            {
                McpSchema.ListToolsResult result = client.get().listTools();
                if ( result != null && result.tools() != null )
                {
                    List<String> names = new ArrayList<>();
                    for ( McpSchema.Tool tool : result.tools() )
                    {
                        names.add( tool.name() );
                    }
                    Collections.sort( names );
                    return names;
                }
            }
            catch ( Exception e )
            {
                logger.error( "Failed to list tools for MCP server " + descriptor.name() + ": " + e.getMessage() );
            }
        }
        return Collections.emptyList();
    }

    public void toggleToolEnabled( int serverIndex, String toolName, boolean enabled )
    {
        List<McpServerDescriptor> servers = mcpServerRepository.listStoredServers();
        if ( serverIndex >= 0 && serverIndex < servers.size() )
        {
            McpServerDescriptor server = servers.get( serverIndex );
            List<String> excludedTools = new ArrayList<>( server.excludedTools() );
            if ( enabled )
            {
                excludedTools.remove( toolName );
            }
            else
            {
                if ( !excludedTools.contains( toolName ) )
                {
                    excludedTools.add( toolName );
                }
            }
            McpServerDescriptor updated = new McpServerDescriptor( server.uid(),
                    server.name(), server.command(), server.environmentVariables(),
                    server.enabled(), server.builtIn(), excludedTools, server.url() );
            mcpServerRepository.upsertStoredServer( updated );
            restartServers();
        }
    }

    private void restartServers()
    {
        clientRetistry.restart();
        httpMcpServerRegistry.restart();
        Activator.getDefault().make( AgentSessionManager.class ).refreshMcpToolsOnAllSessions();
    }

    /**
     * Register the view
     * 
     * @param mcpServerPreferencePage
     *            the view to register
     */
    public void registerView( McpServerPreferencePage mcpServerPreferencePage )
    {
        view = mcpServerPreferencePage;
        McpServerPreferencesLog.info( "registerView: presenter@" + System.identityHashCode( this )
                + " repository@" + System.identityHashCode( mcpServerRepository ) );
        view.showServers( getServersWithStatus() );
        view.setDetailsEditable( false );
    }

    /**
     * Reset to default values
     */
    public void onPerformDefaults()
    {
        mcpServerRepository.setToDefault();
        restartServers();
        view.showServers( getServersWithStatus() );
        view.clearServerDetails();
        view.setDetailsEditable( false );
    }

    private static Map<String, String> toHeaderMap( List<EnvironmentVariable> environmentVariables )
    {
        Map<String, String> headers = new HashMap<>();
        if ( environmentVariables == null )
        {
            return headers;
        }
        for ( EnvironmentVariable variable : environmentVariables )
        {
            if ( variable.name() != null && !variable.name().isBlank() )
            {
                headers.put( variable.name(), variable.value() != null ? variable.value() : "" );
            }
        }
        return headers;
    }
}
