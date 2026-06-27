package com.rubberjam.eclipse.assistai.mcp;

import java.util.Collections;
import java.util.List;

/**
 * Descriptor for MCP Server configuration
 */
public record McpServerDescriptor( String uid, 
                                   String name, 
                                   String command,
                                   List<EnvironmentVariable> environmentVariables, 
                                   boolean enabled, 
                                   boolean builtIn,
                                   List<String> excludedTools,
                                   String url )
{
    public McpServerDescriptor {
        if ( excludedTools == null )
        {
            excludedTools = Collections.emptyList();
        }
        if ( url == null )
        {
            url = "";
        }
    }

    public boolean isHttpServer()
    {
        return url != null && !url.isBlank();
    }
    
    public enum Status { DISABLED, NOT_CONNECTED, RUNNING, FAILED }
    public record EnvironmentVariable( String name, String value ) {};
    
    public record McpServerDescriptorWithStatus ( McpServerDescriptor descriptor, Status status ) {}; 
    
}
