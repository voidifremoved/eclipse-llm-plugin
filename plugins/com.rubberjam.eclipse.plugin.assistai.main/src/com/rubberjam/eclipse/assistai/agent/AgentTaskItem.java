package com.rubberjam.eclipse.assistai.agent;

/**
 * One checklist row parsed from an assistant plan message.
 */
public final class AgentTaskItem
{
    private final String text;

    private boolean done;

    public AgentTaskItem( String text, boolean done )
    {
        this.text = text != null ? text : "";
        this.done = done;
    }

    public String text()
    {
        return text;
    }

    public boolean isDone()
    {
        return done;
    }

    public void setDone( boolean done )
    {
        this.done = done;
    }
}
