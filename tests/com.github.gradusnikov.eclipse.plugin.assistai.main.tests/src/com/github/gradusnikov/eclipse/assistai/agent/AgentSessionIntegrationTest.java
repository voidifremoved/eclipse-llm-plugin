package com.github.gradusnikov.eclipse.assistai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


public class AgentSessionIntegrationTest {

    @Test
    public void testAgentSessionInitialization() {
        ChatModelRegistry modelRegistry = new ChatModelRegistry();
        McpToolBridge toolBridge = new McpToolBridge();

        AgentSession session = new AgentSession(modelRegistry, toolBridge, "You are a helpful assistant.");

        assertNotNull(session.getHistory());
        assertEquals(1, session.getHistory().size(), "History should contain the system prompt initially.");
        assertTrue(session.getHistory().get(0).getContent().contains("helpful assistant"));
    }
}
