package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.List;

public record AgentTabDescriptor(
        String tabId,
        String title,
        String modelUid,
        String draftText,
        boolean active,
        List<AgentMessageSnapshot> messages )
{
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
    }
}
