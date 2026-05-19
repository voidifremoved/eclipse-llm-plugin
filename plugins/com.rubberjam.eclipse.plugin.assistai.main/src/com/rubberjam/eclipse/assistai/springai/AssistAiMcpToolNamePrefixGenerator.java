package com.rubberjam.eclipse.assistai.springai;

import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;

import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Prefixes MCP tools with the AssistAI server name ({@code eclipse-ide__getSource}).
 */
public final class AssistAiMcpToolNamePrefixGenerator implements McpToolNamePrefixGenerator
{
    private final String serverName;

    public AssistAiMcpToolNamePrefixGenerator( String serverName )
    {
        if ( serverName == null || serverName.isBlank() )
        {
            throw new IllegalArgumentException( "serverName must not be blank" );
        }
        this.serverName = serverName;
    }

    @Override
    public String prefixedToolName( McpConnectionInfo connectionInfo, Tool tool )
    {
        return AssistAiMcpToolNames.prefixed( serverName, tool.name() );
    }
}
