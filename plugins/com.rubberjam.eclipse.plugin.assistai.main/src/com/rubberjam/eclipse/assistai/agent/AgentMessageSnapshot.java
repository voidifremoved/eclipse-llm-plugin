package com.rubberjam.eclipse.assistai.agent;

public record AgentMessageSnapshot(
        String id,
        String role,
        String content )
{
}
