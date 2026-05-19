package com.rubberjam.eclipse.assistai.agent;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;
import com.rubberjam.eclipse.assistai.prompt.PromptLoader;
import com.rubberjam.eclipse.assistai.prompt.PromptContextValueProvider;
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
    @Inject
    private PromptRepository promptRepository;

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
        String project = contextValues.getContextValue( "currentProjectName" );
        String file = contextValues.getContextValue( "currentFileName" );
        String path = contextValues.getContextValue( "currentFilePath" );
        String selection = contextValues.getContextValue( "selectedContent" );
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

        if ( !hasAnyEnabledServer() )
        {
            ctx.append( "(No MCP servers are enabled in Assist Agent preferences.)\n" );
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
            if ( tier == AgentToolTier.WEB && !agentToolPolicy.isAllowWebTools() )
            {
                continue;
            }
            if ( tier == AgentToolTier.UTILITY && !"memory".equals( server.name() ) )
            {
                continue;
            }
            any = true;
            lines.append( "  - " ).append( server.name() );
            if ( server.builtIn() )
            {
                lines.append( " (built-in)" );
            }
            lines.append( '\n' );
        }
        if ( any )
        {
            ctx.append( label ).append( ":\n" );
            ctx.append( lines );
        }
    }

    private boolean hasAnyEnabledServer()
    {
        for ( McpServerDescriptor server : mcpServerRepository.listStoredServers() )
        {
            if ( server.enabled() )
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
