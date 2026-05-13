package com.github.gradusnikov.eclipse.assistai.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.gradusnikov.eclipse.assistai.mcp.local.InMemoryMcpClientRetistry;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.tool.ToolCallback;

public class McpToolBridgeTest
{
    @Test
    public void testGetToolCallbacksThrowsNullPointerIfNoRegistry()
    {
        McpToolBridge mcpToolBridge = new McpToolBridge();
        assertThrows( NullPointerException.class, () -> mcpToolBridge.getToolCallbacks() );
    }
}
