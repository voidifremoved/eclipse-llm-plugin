package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.chat.ConversationContext;
import com.rubberjam.eclipse.assistai.mcp.local.InMemoryMcpClientRetistry;

import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

/**
 * Exposes enabled in-process MCP clients as Spring AI {@link ToolCallback} instances.
 */
@Creatable
@Singleton
public class McpToolBridge
{
    @Inject
    private Provider<InMemoryMcpClientRetistry> mcpClientRegistryProvider;

    /**
     * Spring AI tool callbacks for every enabled MCP client.
     */
    public ToolCallback[] getToolCallbacks()
    {
        List<McpSyncClient> clients = new ArrayList<>( mcpClientRegistryProvider.get().listEnabledClients().values() );
        List<ToolCallback> callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks( clients );
        return callbacks.toArray( new ToolCallback[0] );
    }

    /**
     * Reserved for future filtering (for example, restricting tools by conversation scope).
     * Currently returns the same callbacks as {@link #getToolCallbacks()}.
     */
    public ToolCallback[] getToolCallbacks( ConversationContext context )
    {
        Objects.requireNonNull( context, "context" );
        return getToolCallbacks();
    }
}
