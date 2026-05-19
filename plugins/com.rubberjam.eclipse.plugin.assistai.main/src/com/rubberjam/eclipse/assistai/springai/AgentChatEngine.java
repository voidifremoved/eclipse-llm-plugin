package com.rubberjam.eclipse.assistai.springai;

import org.eclipse.e4.core.di.annotations.Creatable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for Spring AI–backed agent conversations.
 */
@Creatable
@Singleton
public class AgentChatEngine
{
    @Inject
    private ChatModelRegistry modelRegistry;

    @Inject
    private McpToolBridge toolBridge;

    public AgentChatSession createSession( String systemPrompt )
    {
        return new AgentChatSession( modelRegistry, toolBridge, systemPrompt );
    }
}
