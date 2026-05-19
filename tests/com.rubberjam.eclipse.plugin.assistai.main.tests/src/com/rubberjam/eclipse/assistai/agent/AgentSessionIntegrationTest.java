package com.rubberjam.eclipse.assistai.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.rubberjam.eclipse.assistai.springai.AgentChatSession;
import com.rubberjam.eclipse.assistai.springai.ChatModelRegistry;
import com.rubberjam.eclipse.assistai.springai.McpToolBridge;

public class AgentSessionIntegrationTest
{
    @Test
    public void testAgentSessionInitialization()
    {
        ChatModelRegistry modelRegistry = new ChatModelRegistry();
        McpToolBridge toolBridge = new McpToolBridge();
        AgentChatSession chatSession = new AgentChatSession(
                modelRegistry,
                toolBridge,
                "You are a helpful assistant." );
        AgentSession session = new AgentSession( chatSession );

        assertNotNull( session.getSessionId() );
        assertNotNull( session.getHistory() );
    }
}
