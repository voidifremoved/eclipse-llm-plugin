package com.rubberjam.eclipse.assistai.springai;

import java.util.Set;

/**
 * Per-message options for {@link AgentChatSession}.
 */
public final class AgentSendOptions
{
    public static final AgentSendOptions DEFAULT = new AgentSendOptions( true, null, null, null );

    public static final AgentSendOptions PLAN_ONLY = new AgentSendOptions(
            false,
            "agent-plan-mode.md",
            null,
            null );

    private final boolean toolsEnabled;

    /** Bundled prompt fragment filename under {@code prompts/}, or {@code null}. */
    private final String promptFragmentFile;

    private final String additionalSystemText;

    /** When non-null, restricts registered tool callbacks to this set. */
    private final Set<String> allowedToolsOverride;

    public AgentSendOptions(
            boolean toolsEnabled,
            String promptFragmentFile,
            String additionalSystemText,
            Set<String> allowedToolsOverride )
    {
        this.toolsEnabled = toolsEnabled;
        this.promptFragmentFile = promptFragmentFile;
        this.additionalSystemText = additionalSystemText;
        this.allowedToolsOverride = allowedToolsOverride;
    }

    public static AgentSendOptions askMode( Set<String> readOnlyTools )
    {
        return new AgentSendOptions( true, null, null, readOnlyTools );
    }

    public static AgentSendOptions executePlan( String approvedPlanText )
    {
        return new AgentSendOptions( true, null, approvedPlanText, null );
    }

    public boolean isToolsEnabled()
    {
        return toolsEnabled;
    }

    public String getPromptFragmentFile()
    {
        return promptFragmentFile;
    }

    public String getAdditionalSystemText()
    {
        return additionalSystemText;
    }

    public Set<String> getAllowedToolsOverride()
    {
        return allowedToolsOverride;
    }
}
