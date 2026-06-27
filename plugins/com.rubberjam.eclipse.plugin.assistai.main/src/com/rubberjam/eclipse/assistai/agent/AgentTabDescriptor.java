package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.List;

public record AgentTabDescriptor(
        String tabId,
        String title,
        String modelUid,
        String draftText,
        boolean active,
        List<AgentMessageSnapshot> messages,
        String interactionMode )
{
    public AgentTabDescriptor(
            String tabId,
            String title,
            String modelUid,
            String draftText,
            boolean active,
            List<AgentMessageSnapshot> messages )
    {
        this( tabId, title, modelUid, draftText, active, messages, AgentInteractionMode.AGENT.name() );
    }

    public AgentTabDescriptor
    {
        if ( messages == null )
        {
            messages = new ArrayList<>();
        }
        if ( draftText == null )
        {
            draftText = "";
        }
        if ( interactionMode == null || interactionMode.isBlank() )
        {
            interactionMode = AgentInteractionMode.AGENT.name();
        }
    }
}
