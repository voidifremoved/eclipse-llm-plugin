package com.rubberjam.eclipse.assistai.agent;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;
import com.rubberjam.eclipse.assistai.prompt.PromptRepository;
import com.rubberjam.eclipse.assistai.prompt.Prompts;
import com.rubberjam.eclipse.assistai.resources.ResourceCache;
import com.rubberjam.eclipse.assistai.resources.CachedResource;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Creatable
@Singleton
public class AgentSystemPromptBuilder
{
    @Inject private PromptRepository promptRepository;
    @Inject private ResourceCache resourceCache;
    @Inject private McpServerRepository mcpServerRepository;
    public String buildSystemPrompt()
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append(promptRepository.getPrompt(Prompts.SYSTEM.name()));
        prompt.append("\n");
        prompt.append(buildMcpToolsContext());
        prompt.append(buildResourceContext());
        return prompt.toString();
    }

    private String buildMcpToolsContext()
    {
        StringBuilder ctx = new StringBuilder();
        ctx.append("\n=== Available MCP tools ===\n");
        ctx.append( "You can call MCP tools to inspect or change the Eclipse workspace. " );
        ctx.append( "Prefer eclipse-ide, eclipse-coder, eclipse-runner, eclipse-context, and eclipse-git " );
        ctx.append( "for project code instead of guessing from chat history alone.\n" );
        boolean any = false;
        for ( McpServerDescriptor server : mcpServerRepository.listStoredServers() )
        {
            if ( server.enabled() )
            {
                any = true;
                ctx.append( "- " ).append( server.name() );
                if ( server.builtIn() )
                {
                    ctx.append( " (built-in Eclipse server)" );
                }
                ctx.append( '\n' );
            }
        }
        if ( !any )
        {
            ctx.append( "(No MCP servers are enabled in Assist Agent preferences.)\n" );
        }
        ctx.append( "===========================\n" );
        return ctx.toString();
    }

    private String buildResourceContext()
    {
        StringBuilder ctx = new StringBuilder();
        if (resourceCache != null && !resourceCache.isEmpty()) {
            ctx.append("\n=== User Shared Context ===\n");
            for (CachedResource res : resourceCache.getAll().values()) {
                ctx.append(res.content()).append("\n");
            }
            ctx.append("===========================\n");
        }
        return ctx.toString();
    }
}
