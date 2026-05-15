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

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

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
        return getToolCallbacks( ToolCallEventListener.noop() );
    }

    public ToolCallback[] getToolCallbacks( ToolCallEventListener listener )
    {
        List<McpSyncClient> clients = new ArrayList<>( mcpClientRegistryProvider.get().listEnabledClients().values() );
        List<ToolCallback> callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks( clients );
        List<ToolCallback> wrapped = new ArrayList<>();
        for ( ToolCallback callback : callbacks )
        {
            wrapped.add( new ObservableToolCallback( callback, listener ) );
        }
        return wrapped.toArray( new ToolCallback[0] );
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

    private static final class ObservableToolCallback implements ToolCallback
    {
        private final ToolCallback delegate;

        private final ToolCallEventListener listener;

        private ObservableToolCallback( ToolCallback delegate, ToolCallEventListener listener )
        {
            this.delegate = Objects.requireNonNull( delegate, "delegate" );
            this.listener = listener != null ? listener : ToolCallEventListener.noop();
        }

        @Override
        public ToolDefinition getToolDefinition()
        {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata()
        {
            return delegate.getToolMetadata();
        }

        @Override
        public String call( String toolInput )
        {
            return call( toolInput, null );
        }

        @Override
        public String call( String toolInput, ToolContext toolContext )
        {
            String id = java.util.UUID.randomUUID().toString();
            String toolName = getToolDefinition().name();
            listener.onToolCallEvent( new ToolCallEvent(
                    id,
                    toolName,
                    toolInput,
                    null,
                    ToolCallStatus.STARTED ) );
            try
            {
                String result = delegate.call( toolInput, toolContext );
                listener.onToolCallEvent( new ToolCallEvent(
                        id,
                        toolName,
                        toolInput,
                        result,
                        ToolCallStatus.FINISHED ) );
                return result;
            }
            catch ( RuntimeException e )
            {
                listener.onToolCallEvent( new ToolCallEvent(
                        id,
                        toolName,
                        toolInput,
                        e.getMessage(),
                        ToolCallStatus.FAILED ) );
                throw e;
            }
        }
    }
}
