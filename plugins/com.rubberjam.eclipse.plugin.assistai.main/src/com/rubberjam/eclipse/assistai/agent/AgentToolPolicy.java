package com.rubberjam.eclipse.assistai.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jface.preference.IPreferenceStore;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;
import com.rubberjam.eclipse.assistai.preferences.PreferenceConstants;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Resolves which MCP tools the in-IDE agent may call (workspace-first by default).
 */
@Creatable
@Singleton
public class AgentToolPolicy
{
    private static final Set<String> WORKSPACE_SERVER_NAMES = Set.of(
            "eclipse-ide",
            "eclipse-coder",
            "eclipse-runner",
            "eclipse-context",
            "eclipse-git",
            "eclipse-pde" );

    private static final Set<String> WEB_SERVER_NAMES = Set.of(
            "duck-duck-search",
            "webpage-reader" );

    private static final Set<String> UTILITY_SERVER_NAMES = Set.of(
            "memory",
            "time" );

    /** Utility servers exposed to the agent when enabled (excludes {@code time} by default). */
    private static final Set<String> AGENT_UTILITY_SERVER_NAMES = Set.of(
            "memory" );

    @Inject
    private McpServerRepository mcpServerRepository;

    public AgentToolTier tierForServer( String serverName, boolean builtIn )
    {
        Objects.requireNonNull( serverName );
        if ( WEB_SERVER_NAMES.contains( serverName ) )
        {
            return AgentToolTier.WEB;
        }
        if ( UTILITY_SERVER_NAMES.contains( serverName ) )
        {
            return AgentToolTier.UTILITY;
        }
        if ( WORKSPACE_SERVER_NAMES.contains( serverName ) )
        {
            return AgentToolTier.WORKSPACE;
        }
        if ( builtIn )
        {
            return AgentToolTier.WORKSPACE;
        }
        return AgentToolTier.USER;
    }

    public boolean isAllowWebTools()
    {
        return getPreferenceStore().getBoolean( PreferenceConstants.ASSISTAI_AGENT_ALLOW_WEB_TOOLS );
    }

    public void setAllowWebTools( boolean allow )
    {
        getPreferenceStore().setValue( PreferenceConstants.ASSISTAI_AGENT_ALLOW_WEB_TOOLS, allow );
    }

    public boolean isUseEclipseSkillsInPrompt()
    {
        return getPreferenceStore().getBoolean( PreferenceConstants.ASSISTAI_AGENT_USE_ECLIPSE_SKILLS );
    }

    public void setUseEclipseSkillsInPrompt( boolean use )
    {
        getPreferenceStore().setValue( PreferenceConstants.ASSISTAI_AGENT_USE_ECLIPSE_SKILLS, use );
    }

    /**
     * Full Spring AI tool names ({@code server__tool}) allowed for the agent.
     */
    public Set<String> resolveAllowedToolNames()
    {
        Set<String> allowed = new HashSet<>();
        boolean allowWeb = isAllowWebTools();
        for ( McpServerDescriptor server : mcpServerRepository.listStoredServers() )
        {
            if ( !server.enabled() )
            {
                continue;
            }
            AgentToolTier tier = tierForServer( server.name(), server.builtIn() );
            if ( tier == AgentToolTier.WEB && !allowWeb )
            {
                continue;
            }
            if ( tier == AgentToolTier.UTILITY && !AGENT_UTILITY_SERVER_NAMES.contains( server.name() ) )
            {
                continue;
            }
            List<String> tools = mcpServerRepository.listToolsForServer( server.name() );
            for ( String tool : tools )
            {
                if ( !server.excludedTools().contains( tool ) )
                {
                    allowed.add( server.name() + "__" + tool );
                }
            }
        }
        return Set.copyOf( allowed );
    }

    /**
     * Enables workspace Eclipse servers and memory; disables web/time builtins.
     */
    public void applyWorkspaceAgentPreset()
    {
        setAllowWebTools( false );
        for ( McpServerDescriptor server : mcpServerRepository.listStoredServers() )
        {
            if ( !server.builtIn() )
            {
                continue;
            }
            boolean enable = WORKSPACE_SERVER_NAMES.contains( server.name() )
                    || AGENT_UTILITY_SERVER_NAMES.contains( server.name() );
            if ( server.enabled() == enable )
            {
                continue;
            }
            McpServerDescriptor updated = new McpServerDescriptor(
                    server.uid(),
                    server.name(),
                    server.command(),
                    server.environmentVariables(),
                    enable,
                    server.builtIn(),
                    server.excludedTools(),
                    server.url() );
            mcpServerRepository.upsertStoredServer( updated );
        }
    }

    public boolean isWorkspacePresetServer( String serverName )
    {
        return WORKSPACE_SERVER_NAMES.contains( serverName )
                || AGENT_UTILITY_SERVER_NAMES.contains( serverName );
    }

    private IPreferenceStore getPreferenceStore()
    {
        return Activator.getDefault().getPreferenceStore();
    }
}
