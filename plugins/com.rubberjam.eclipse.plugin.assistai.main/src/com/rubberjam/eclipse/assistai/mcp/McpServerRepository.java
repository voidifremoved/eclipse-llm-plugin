package com.rubberjam.eclipse.assistai.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.preferences.PreferenceConstants;
import com.rubberjam.eclipse.assistai.preferences.mcp.McpServerDescriptorUtilities;
import com.rubberjam.eclipse.assistai.preferences.mcp.McpServerPreferencesLog;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Creatable
@Singleton
public class McpServerRepository
{
    private final ILog logger;
    private final McpServerBuiltins mcpBuiltins;
    

    @Inject
    public McpServerRepository(McpServerBuiltins mcpServerBuiltins,  ILog logger)
    {
        Objects.requireNonNull( mcpServerBuiltins );
        Objects.requireNonNull( logger );
        this.mcpBuiltins = mcpServerBuiltins;
        this.logger = logger;
    }
    
    public IPreferenceStore getPreferenceStore()
    {
        return  Activator.getDefault().getPreferenceStore();
    }
    
    /**
     * Retrieves all defined MCP servers from preferences.
     *
     * @return A list of MCP server descriptors.
     */
    /**
     * Servers as persisted in preferences (no built-in merge).
     */
    public List<McpServerDescriptor> listRawStoredServers()
    {
        String serversJson = getPreferenceStore().getString( PreferenceConstants.ASSISTAI_DEFINED_MCP_SERVERS );
        boolean isDefault = getPreferenceStore().isDefault( PreferenceConstants.ASSISTAI_DEFINED_MCP_SERVERS );
        McpServerPreferencesLog.info( "listRawStoredServers: isDefault=" + isDefault
                + " jsonLength=" + ( serversJson != null ? serversJson.length() : 0 )
                + " repository@" + System.identityHashCode( this ) );
        List<McpServerDescriptor> filtered = filterPersistedEntries( McpServerDescriptorUtilities.fromJson( serversJson ) );
        McpServerPreferencesLog.logDescriptors( "listRawStoredServers: filtered", filtered );
        return filtered;
    }

    /**
     * Built-in servers plus stored preferences (for UI and runtime).
     */
    public List<McpServerDescriptor> listStoredServers()
    {
        List<McpServerDescriptor> merged = mergeWithBuiltins( listRawStoredServers() );
        McpServerPreferencesLog.logDescriptors( "listStoredServers: merged", merged );
        return merged;
    }

    /**
     * Insert or replace a server entry in preferences (matched by {@code uid}).
     */
    public void upsertStoredServer( McpServerDescriptor descriptor )
    {
        Objects.requireNonNull( descriptor );
        McpServerPreferencesLog.info( "upsertStoredServer: " + McpServerPreferencesLog.describe( descriptor ) );
        List<McpServerDescriptor> raw = listRawStoredServers();
        for ( int i = 0; i < raw.size(); i++ )
        {
            if ( descriptor.uid().equals( raw.get( i ).uid() ) )
            {
                McpServerPreferencesLog.info( "upsertStoredServer: replacing existing index " + i );
                raw.set( i, descriptor );
                save( raw );
                return;
            }
        }
        McpServerPreferencesLog.info( "upsertStoredServer: adding new entry at index " + raw.size() );
        raw.add( descriptor );
        save( raw );
    }

    /**
     * Remove a server from preferences by {@code uid}.
     */
    public void removeStoredServerByUid( String uid )
    {
        Objects.requireNonNull( uid );
        List<McpServerDescriptor> raw = listRawStoredServers();
        raw.removeIf( server -> uid.equals( server.uid() ) );
        save( raw );
    }

    /**
     * Always includes built-in AssistAI MCP servers, overlaying stored enable/tool preferences when present.
     */
    private List<McpServerDescriptor> mergeWithBuiltins( List<McpServerDescriptor> stored )
    {
        List<McpServerDescriptor> builtins = mcpBuiltins.listBuiltInImplementations();
        List<McpServerDescriptor> merged = new ArrayList<>();

        for ( McpServerDescriptor builtin : builtins )
        {
            McpServerDescriptor fromStore = findStoredServer( stored, builtin );
            if ( fromStore != null )
            {
                merged.add( asBuiltIn( fromStore ) );
            }
            else
            {
                merged.add( builtin );
            }
        }

        for ( McpServerDescriptor server : stored )
        {
            if ( !server.builtIn() )
            {
                McpServerDescriptor conflictingBuiltin = findBuiltinByUidOrName( builtins, server );
                if ( conflictingBuiltin == null )
                {
                    merged.add( server );
                }
                else
                {
                    McpServerPreferencesLog.warn( "mergeWithBuiltins: skipping user server '"
                            + server.name()
                            + "' because it conflicts with built-in '"
                            + conflictingBuiltin.name()
                            + "' (use a different name)" );
                }
            }
        }

        return merged;
    }

    private static McpServerDescriptor findStoredServer( List<McpServerDescriptor> stored, McpServerDescriptor builtin )
    {
        for ( McpServerDescriptor server : stored )
        {
            if ( builtin.uid().equals( server.uid() ) || builtin.name().equals( server.name() ) )
            {
                return server;
            }
        }
        return null;
    }

    private static McpServerDescriptor findBuiltinByUidOrName( List<McpServerDescriptor> builtins, McpServerDescriptor server )
    {
        for ( McpServerDescriptor builtin : builtins )
        {
            if ( builtin.uid().equals( server.uid() ) || builtin.name().equals( server.name() ) )
            {
                return builtin;
            }
        }
        return null;
    }

    private static McpServerDescriptor asBuiltIn( McpServerDescriptor descriptor )
    {
        if ( descriptor.builtIn() )
        {
            return descriptor;
        }
        return new McpServerDescriptor(
                descriptor.uid(),
                descriptor.name(),
                descriptor.command(),
                descriptor.environmentVariables(),
                descriptor.enabled(),
                true,
                descriptor.excludedTools(),
                descriptor.url() );
    }

    public List<McpServerDescriptor> listBuiltInServers()
    {
        return mcpBuiltins.listBuiltInImplementations();
    }

    public Class<?> findImplementation( String name )
    {
        return mcpBuiltins.findImplementation( name );
    }
    
    public Object makeImplementation( String name )
    {
        var clazz = findImplementation( name );
        var implementation =  Activator.getDefault().make( clazz );
        Objects.requireNonNull( implementation, "No actual object of class " + clazz + " found!" );
        return implementation;
        
    }
    
    /**
     * Save the list of servers
     * 
     * @param servers
     *            the servers to save
     */
    public void save( List<McpServerDescriptor> servers )
    {
        List<McpServerDescriptor> toPersist = filterPersistedEntries( servers );
        McpServerPreferencesLog.logDescriptors( "save: input", servers );
        McpServerPreferencesLog.logDescriptors( "save: toPersist", toPersist );
        String json = McpServerDescriptorUtilities.toJson( toPersist );
        McpServerPreferencesLog.info( "save: jsonLength=" + json.length() );
        IPreferenceStore store = getPreferenceStore();
        store.setValue( PreferenceConstants.ASSISTAI_DEFINED_MCP_SERVERS, json );
        if ( store instanceof IPersistentPreferenceStore persistentStore )
        {
            try
            {
                persistentStore.save();
            }
            catch ( java.io.IOException e )
            {
                logger.error( "Failed to persist MCP server preferences", e );
            }
        }
        logger.info( "MCP Servers Updated (" + toPersist.size() + " stored entries)" );
    }

    /**
     * Preferences should only persist user-defined servers and built-in customizations
     * (enabled / excluded tools), not full built-in snapshots from defaults.
     */
    private List<McpServerDescriptor> filterPersistedEntries( List<McpServerDescriptor> servers )
    {
        List<McpServerDescriptor> builtins = mcpBuiltins.listBuiltInImplementations();
        List<McpServerDescriptor> filtered = new ArrayList<>();
        for ( McpServerDescriptor server : servers )
        {
            if ( !server.builtIn() )
            {
                filtered.add( server );
                McpServerPreferencesLog.info( "filterPersistedEntries: keep user server "
                        + McpServerPreferencesLog.describe( server ) );
                continue;
            }
            McpServerDescriptor canonical = findBuiltinByUidOrName( builtins, server );
            if ( canonical != null && isBuiltinPreferenceOverride( canonical, server ) )
            {
                filtered.add( server );
                McpServerPreferencesLog.info( "filterPersistedEntries: keep built-in override "
                        + server.name() );
            }
            else
            {
                McpServerPreferencesLog.info( "filterPersistedEntries: drop built-in snapshot "
                        + server.name() );
            }
        }
        return filtered;
    }

    private static boolean isBuiltinPreferenceOverride( McpServerDescriptor canonical, McpServerDescriptor stored )
    {
        return canonical.enabled() != stored.enabled()
                || !canonical.excludedTools().equals( stored.excludedTools() );
    }

    public void setToDefault()
    {
        getPreferenceStore().setToDefault( PreferenceConstants.ASSISTAI_DEFINED_MCP_SERVERS );
        logger.info( "MCP Servers re-set to defaults" );
    }

    public List<String> listToolsForServer( String serverName )
    {
        try
        {
            Class<?> clazz = findImplementation( serverName );
            return Stream.of( clazz.getDeclaredMethods() )
                    .filter( m -> m.isAnnotationPresent( com.rubberjam.eclipse.assistai.mcp.annotations.Tool.class ) )
                    .map( m -> m.getAnnotation( com.rubberjam.eclipse.assistai.mcp.annotations.Tool.class ).name() )
                    .sorted()
                    .collect( Collectors.toList() );
        }
        catch ( IllegalArgumentException e )
        {
            return Collections.emptyList();
        }
    }
}
