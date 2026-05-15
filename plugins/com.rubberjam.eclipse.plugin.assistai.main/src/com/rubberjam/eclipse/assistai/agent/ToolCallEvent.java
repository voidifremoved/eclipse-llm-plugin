package com.rubberjam.eclipse.assistai.agent;

public record ToolCallEvent(
        String id,
        String toolName,
        String input,
        String output,
        ToolCallStatus status )
{
}
