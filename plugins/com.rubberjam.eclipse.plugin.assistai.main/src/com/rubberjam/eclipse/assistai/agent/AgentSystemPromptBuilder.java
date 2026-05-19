package com.rubberjam.eclipse.assistai.agent;

import java.util.List;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;
import com.rubberjam.eclipse.assistai.prompt.PromptLoader;
import com.rubberjam.eclipse.assistai.prompt.PromptContextValueProvider;
import com.rubberjam.eclipse.assistai.resources.ResourceCache;
import com.rubberjam.eclipse.assistai.resources.CachedResource;
import com.rubberjam.eclipse.assistai.springai.AssistAiMcpToolNames;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Creatable
@Singleton
public class AgentSystemPromptBuilder
{
    @Inject
    private ResourceCache resourceCache;

    @Inject
    private McpServerRepository mcpServerRepository;

    @Inject
    private PromptContextValueProvider contextValues;

    @Inject
    private AgentToolPolicy agentToolPolicy;

    @Inject
    private PromptLoader promptLoader;

    public String buildSystemPrompt()
    {
        return buildSystemPrompt( null, null );
    }

    public String buildSystemPrompt( String extraFragmentFile, String additionalSystemText )
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append( loadBundledFragment( "agent-system-prompt.md" ) );
        prompt.append( "\n" );
        if ( extraFragmentFile != null && !extraFragmentFile.isBlank() )
        {
            prompt.append( loadBundledFragment( extraFragmentFile ) );
        }
        if ( additionalSystemText != null && !additionalSystemText.isBlank() )
        {
            prompt.append( "\n=== Approved plan ===\n" );
            prompt.append( additionalSystemText );
            prompt.append( "\nExecute this plan with workspace tools. Mark steps done as you go.\n" );
            prompt.append( "===========================\n" );
        }
        prompt.append( buildWorkspaceContext() );
        prompt.append( buildMcpToolsContext() );
        prompt.append( loadBundledFragment( "agent-workspace.md" ) );
        if ( agentToolPolicy.isUseEclipseSkillsInPrompt() )
        {
            prompt.append( loadBundledFragment( "agent-skills-summary.md" ) );
        }
        prompt.append( buildResourceContext() );
        return prompt.toString();
    }

    private String buildWorkspaceContext()
    {
        StringBuilder ctx = new StringBuilder();
        ctx.append( "\n=== Current Eclipse workspace ===\n" );
        String project = contextValues.getContextValue( PromptContextValueProvider.CURRENT_PROJECT_NAME );
        String file = contextValues.getContextValue( PromptContextValueProvider.CURRENT_FILE_NAME );
        String path = contextValues.getContextValue( PromptContextValueProvider.CURRENT_FILE_PATH );
        String selection = contextValues.getContextValue( PromptContextValueProvider.SELECTED_CONTENT );
        if ( project != null && !project.isBlank() )
        {
            ctx.append( "Active project: " ).append( project ).append( '\n' );
        }
        else
        {
            ctx.append( "Active project: (none)\n" );
        }
        if ( file != null && !file.isBlank() )
        {
            ctx.append( "Open editor file: " ).append( file );
            if ( path != null && !path.isBlank() )
            {
                ctx.append( " (" ).append( path ).append( ')' );
            }
            ctx.append( '\n' );
        }
        else
        {
            ctx.append( "Open editor file: (none)\n" );
        }
        if ( selection != null && !selection.isBlank() )
        {
            ctx.append( "Editor selection:\n" ).append( selection ).append( '\n' );
        }
        ctx.append( "Inspect or change code with workspace MCP tools before relying on chat history.\n" );
        ctx.append( "Current file content is not inlined; use readProjectResource/getClassOutline/getMethodSource when needed.\n" );
        ctx.append( "===========================\n" );
        return ctx.toString();
    }

    private String buildMcpToolsContext()
    {
        StringBuilder ctx = new StringBuilder();
        ctx.append( "\n=== Available MCP tools ===\n" );
        ctx.append( "Tool names use the form server__toolName (e.g. eclipse-ide__getCompilationErrors).\n" );
        ctx.append( "Policy: workspace Eclipse servers are preferred; web search tools are " );
        ctx.append( agentToolPolicy.isAllowWebTools() ? "enabled" : "disabled" );
        ctx.append( " in preferences.\n\n" );

        appendTierSection( ctx, "Workspace (IDE, edit, run, git, PDE)", AgentToolTier.WORKSPACE );
        appendTierSection( ctx, "Utility", AgentToolTier.UTILITY );
        if ( agentToolPolicy.isAllowWebTools() )
        {
            appendTierSection( ctx, "Web", AgentToolTier.WEB );
        }
        appendTierSection( ctx, "User-defined servers", AgentToolTier.USER );

        if ( !hasAnyAgentServer() )
        {
            ctx.append( "(No MCP servers are enabled for the agent policy.)\n" );
        }
        ctx.append( "===========================\n" );
        return ctx.toString();
    }

    private void appendTierSection( StringBuilder ctx, String label, AgentToolTier tier )
    {
        boolean any = false;
        StringBuilder lines = new StringBuilder();
        for ( McpServerDescriptor server : mcpServerRepository.listStoredServers() )
        {
            if ( !server.enabled() )
            {
                continue;
            }
            if ( agentToolPolicy.tierForServer( server.name(), server.builtIn() ) != tier )
            {
                continue;
            }
            if ( !agentToolPolicy.isServerAllowedForAgent( server ) )
            {
                continue;
            }
            any = true;
            lines.append( "  - " ).append( server.name() );
            if ( server.builtIn() )
            {
                lines.append( " (built-in)" );
            }
            appendAllowedToolNames( lines, server );
            lines.append( '\n' );
        }
        if ( any )
        {
            ctx.append( label ).append( ":\n" );
            ctx.append( lines );
        }
    }

    private void appendAllowedToolNames( StringBuilder lines, McpServerDescriptor server )
    {
        List<String> tools = mcpServerRepository.listToolsForServer( server.name() );
        boolean first = true;
        for ( String tool : tools )
        {
            if ( server.excludedTools().contains( tool ) )
            {
                continue;
            }
            if ( first )
            {
                lines.append( ": " );
                first = false;
            }
            else
            {
                lines.append( ", " );
            }
            lines.append( AssistAiMcpToolNames.prefixed( server.name(), tool ) );
        }
    }

    private boolean hasAnyAgentServer()
    {
        for ( McpServerDescriptor server : mcpServerRepository.listStoredServers() )
        {
            if ( agentToolPolicy.isServerAllowedForAgent( server ) )
            {
                return true;
            }
        }
        return false;
    }

    private String loadBundledFragment( String resourceFile )
    {
        try
        {
            return promptLoader.getDefaultPrompt( resourceFile );
        }
        catch ( RuntimeException e )
        {
            return "";
        }
    }

    private String buildResourceContext()
    {
        StringBuilder ctx = new StringBuilder();
        if ( resourceCache != null && !resourceCache.isEmpty() )
        {
            ctx.append( "\n=== User Shared Context ===\n" );
            for ( CachedResource res : resourceCache.getAll().values() )
            {
                ctx.append( res.content() ).append( "\n" );
            }
            ctx.append( "===========================\n" );
        }
        return ctx.toString();
    }
}
