package com.rubberjam.eclipse.assistai.springai;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.agent.AgentToolPolicy;

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

    @Inject
    private AgentToolPolicy agentToolPolicy;

    public AgentChatSession createSession( String systemPrompt )
    {
        return new AgentChatSession( modelRegistry, toolBridge, agentToolPolicy, systemPrompt );
    }
}
