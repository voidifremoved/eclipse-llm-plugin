package com.rubberjam.eclipse.assistai.agent;

@FunctionalInterface
public interface ToolCallEventListener
{
    void onToolCallEvent( ToolCallEvent event );

    static ToolCallEventListener noop()
    {
        return event -> {
        };
    }
}
