package com.rubberjam.eclipse.assistai.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jface.preference.IPreferenceStore;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.preferences.PreferenceConstants;
import com.rubberjam.eclipse.assistai.preferences.mcp.McpServerDescriptorUtilities;

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
        return new ArrayList<>( McpServerDescriptorUtilities.fromJson( serversJson ) );
    }

    /**
     * Built-in servers plus stored preferences (for UI and runtime).
     */
    public List<McpServerDescriptor> listStoredServers()
    {
        return mergeWithBuiltins( listRawStoredServers() );
    }

    /**
     * Insert or replace a server entry in preferences (matched by {@code uid}).
     */
    public void upsertStoredServer( McpServerDescriptor descriptor )
    {
        Objects.requireNonNull( descriptor );
        List<McpServerDescriptor> raw = listRawStoredServers();
        for ( int i = 0; i < raw.size(); i++ )
        {
            if ( descriptor.uid().equals( raw.get( i ).uid() ) )
            {
                raw.set( i, descriptor );
                save( raw );
                return;
            }
        }
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
            if ( !server.builtIn() && findBuiltinByUidOrName( builtins, server ) == null )
            {
                merged.add( server );
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
        String json = McpServerDescriptorUtilities.toJson( servers );
        getPreferenceStore().setValue( PreferenceConstants.ASSISTAI_DEFINED_MCP_SERVERS, json );
        logger.info( "MCP Servers Updated" );
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
