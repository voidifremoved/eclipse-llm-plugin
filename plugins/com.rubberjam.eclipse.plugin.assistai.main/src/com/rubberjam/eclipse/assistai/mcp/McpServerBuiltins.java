package com.rubberjam.eclipse.assistai.mcp;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.mcp.annotations.McpServer;
import com.rubberjam.eclipse.assistai.mcp.servers.EclipseMcpServer;
import com.rubberjam.eclipse.assistai.mcp.servers.MemoryMcpServer;

import jakarta.inject.Singleton;

@Creatable
@Singleton
class McpServerBuiltins
{
    
    public static final Class<?>[] BUILT_IN_MCP_SERVERS = {
            EclipseMcpServer.class,
            MemoryMcpServer.class
    };
    
    public List<McpServerDescriptor> listBuiltInImplementations()
    {
        return Stream.of( BUILT_IN_MCP_SERVERS )
                      .map( this::toBuiltInMcpServerDescriptor )
                      .collect( Collectors.toList() );        
    }
    
    private McpServerDescriptor toBuiltInMcpServerDescriptor( Class<?> clazz )
    {
        String serverName = clazz.getAnnotation( McpServer.class ).name();
        return new McpServerDescriptor( serverName, 
                serverName, 
                "",
                Collections.emptyList(),
                true, 
                true,
                Collections.emptyList(),
                "" );
    }

    public Class<?> findImplementation( String name )
    {
        Objects.requireNonNull( name );
        return Stream.of( BUILT_IN_MCP_SERVERS )
                     .filter( clazz -> clazz.getAnnotation( McpServer.class ).name().equals( name ) )
                     .findAny()
                     .orElseThrow( () -> new IllegalArgumentException( "No implementation for name: " + name  ) );
        
    }
}
