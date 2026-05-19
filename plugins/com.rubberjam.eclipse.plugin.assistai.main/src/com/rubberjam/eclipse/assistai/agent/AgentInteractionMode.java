package com.rubberjam.eclipse.assistai.agent;

/**
 * Per-tab agent interaction mode (Phase 3.1).
 */
public enum AgentInteractionMode
{
    /** Read-only workspace tools (eclipse-ide / eclipse-context). */
    ASK,
    /** Full workspace tool policy. */
    AGENT,
    /** Plan-only pass without tools; use Execute to run with tools. */
    PLAN;

    public static AgentInteractionMode fromPersisted( String value )
    {
        if ( value == null || value.isBlank() )
        {
            return AGENT;
        }
        try
        {
            return AgentInteractionMode.valueOf( value.trim().toUpperCase() );
        }
        catch ( IllegalArgumentException e )
        {
            return AGENT;
        }
    }
}
