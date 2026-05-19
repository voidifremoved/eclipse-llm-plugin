package com.rubberjam.eclipse.assistai.springai;

/**
 * Per-message options for {@link AgentChatSession}.
 */
public final class AgentSendOptions
{
    public static final AgentSendOptions DEFAULT = new AgentSendOptions( true, null, null );

    public static final AgentSendOptions PLAN_ONLY = new AgentSendOptions( false, "agent-plan-mode.md", null );

    private final boolean toolsEnabled;

    /** Bundled prompt fragment filename under {@code prompts/}, or {@code null}. */
    private final String promptFragmentFile;

    private final String additionalSystemText;

    public AgentSendOptions( boolean toolsEnabled, String promptFragmentFile, String additionalSystemText )
    {
        this.toolsEnabled = toolsEnabled;
        this.promptFragmentFile = promptFragmentFile;
        this.additionalSystemText = additionalSystemText;
    }

    public static AgentSendOptions executePlan( String approvedPlanText )
    {
        return new AgentSendOptions( true, null, approvedPlanText );
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
}
