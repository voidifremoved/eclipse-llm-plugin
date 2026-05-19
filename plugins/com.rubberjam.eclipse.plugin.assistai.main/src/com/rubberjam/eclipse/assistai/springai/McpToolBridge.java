package com.rubberjam.eclipse.assistai.springai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.agent.AgentPostEditVerifier;
import com.rubberjam.eclipse.assistai.agent.ToolCallEvent;
import com.rubberjam.eclipse.assistai.agent.ToolCallEventListener;
import com.rubberjam.eclipse.assistai.agent.ToolCallStatus;
import com.rubberjam.eclipse.assistai.chat.ConversationContext;
import com.rubberjam.eclipse.assistai.mcp.local.InMemoryMcpClientRegistry;

import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Exposes enabled in-process MCP clients as Spring AI {@link ToolCallback} instances.
 */
@Creatable
@Singleton
public class McpToolBridge
{
    @Inject
    private Provider<InMemoryMcpClientRegistry> mcpClientRegistryProvider;

    @Inject
    private McpToolInvocationExecutor toolInvocationExecutor;

    @Inject
    private BuiltinMcpToolRouter builtinToolRouter;

    @Inject
    private AgentPostEditVerifier postEditVerifier;

    public ToolCallback[] getToolCallbacks()
    {
        return getToolCallbacks( ToolCallEventListener.noop() );
    }

    public ToolCallback[] getToolCallbacks( ToolCallEventListener listener )
    {
        return getToolCallbacks( null, listener );
    }

    /**
     * Tool callbacks restricted to {@link ConversationContext#getAllowedTools()} when set.
     */
    public ToolCallback[] getToolCallbacks( ConversationContext context )
    {
        return getToolCallbacks( context, ToolCallEventListener.noop() );
    }

    public ToolCallback[] getToolCallbacks( ConversationContext context, ToolCallEventListener listener )
    {
        InMemoryMcpClientRegistry registry = mcpClientRegistryProvider.get();
        registry.ensureClientsReady();
        Map<String, McpSyncClient> clientsByServer = registry.listEnabledClients();
        List<ToolCallback> callbacks = new ArrayList<>();
        for ( Map.Entry<String, McpSyncClient> entry : clientsByServer.entrySet() )
        {
            String serverName = entry.getKey();
            McpSyncClient client = entry.getValue();
            AssistAiMcpToolNamePrefixGenerator prefixGenerator = new AssistAiMcpToolNamePrefixGenerator( serverName );
            ToolCallback[] perServer = SyncMcpToolCallbackProvider.builder()
                    .mcpClients( client )
                    .toolNamePrefixGenerator( prefixGenerator )
                    .build()
                    .getToolCallbacks();
            for ( ToolCallback callback : perServer )
            {
                callbacks.add( callback );
            }
        }
        List<ToolCallback> wrapped = new ArrayList<>();
        for ( ToolCallback callback : callbacks )
        {
            if ( context != null && context.getAllowedTools() != null
                    && !context.isToolAllowed( callback.getToolDefinition().name() ) )
            {
                continue;
            }
            wrapped.add( new ObservableToolCallback(
                    callback,
                    listener,
                    toolInvocationExecutor,
                    builtinToolRouter,
                    postEditVerifier ) );
        }
        addBuiltinFallbackCallbacks( context, listener, wrapped );
        return wrapped.toArray( new ToolCallback[0] );
    }

    /**
     * Registers built-in tools missing from MCP discovery so Spring AI can resolve names like
     * {@code eclipse-ide__getCompilationErrors}.
     */
    private void addBuiltinFallbackCallbacks(
            ConversationContext context,
            ToolCallEventListener listener,
            List<ToolCallback> wrapped )
    {
        if ( context == null || context.getAllowedTools() == null )
        {
            return;
        }
        Set<String> registered = new HashSet<>();
        for ( ToolCallback callback : wrapped )
        {
            registered.add( callback.getToolDefinition().name() );
        }
        for ( String allowedName : context.getAllowedTools() )
        {
            if ( registered.contains( allowedName ) )
            {
                continue;
            }
            if ( !builtinToolRouter.isRegistered( allowedName ) )
            {
                continue;
            }
            ToolCallback fallback = new BuiltinOnlyToolCallback( allowedName );
            wrapped.add( new ObservableToolCallback(
                    fallback,
                    listener,
                    toolInvocationExecutor,
                    builtinToolRouter,
                    postEditVerifier ) );
            registered.add( allowedName );
        }
    }

    private static final class BuiltinOnlyToolCallback implements ToolCallback
    {
        private final ToolDefinition definition;

        BuiltinOnlyToolCallback( String toolName )
        {
            this.definition = DefaultToolDefinition.builder()
                    .name( toolName )
                    .description( "Built-in Eclipse MCP tool (AssistAI in-process)." )
                    .inputSchema( "{\"type\":\"object\",\"properties\":{}}" )
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition()
        {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata()
        {
            return DefaultToolMetadata.builder().build();
        }

        @Override
        public String call( String toolInput )
        {
            return call( toolInput, null );
        }

        @Override
        public String call( String toolInput, ToolContext toolContext )
        {
            throw new IllegalStateException( "Built-in tool must be routed through BuiltinMcpToolRouter" );
        }
    }

    private static final class ObservableToolCallback implements ToolCallback
    {
        private final ToolCallback delegate;

        private final ToolCallEventListener listener;

        private final McpToolInvocationExecutor invocationExecutor;

        private final BuiltinMcpToolRouter builtinToolRouter;

        private final AgentPostEditVerifier postEditVerifier;

        private ObservableToolCallback(
                ToolCallback delegate,
                ToolCallEventListener listener,
                McpToolInvocationExecutor invocationExecutor,
                BuiltinMcpToolRouter builtinToolRouter,
                AgentPostEditVerifier postEditVerifier )
        {
            this.delegate = Objects.requireNonNull( delegate, "delegate" );
            this.listener = listener != null ? listener : ToolCallEventListener.noop();
            this.invocationExecutor = Objects.requireNonNull( invocationExecutor, "invocationExecutor" );
            this.builtinToolRouter = Objects.requireNonNull( builtinToolRouter, "builtinToolRouter" );
            this.postEditVerifier = postEditVerifier;
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
                String result = invocationExecutor.invoke( () -> {
                    Optional<String> builtin = builtinToolRouter.tryInvoke( toolName, toolInput );
                    if ( builtin.isPresent() )
                    {
                        return builtin.get();
                    }
                    return delegate.call( toolInput, toolContext );
                } );
                if ( postEditVerifier != null && postEditVerifier.isCoderTool( toolName ) )
                {
                    result = result + postEditVerifier.verifyAfterEditFooter();
                }
                listener.onToolCallEvent( new ToolCallEvent(
                        id,
                        toolName,
                        toolInput,
                        result,
                        ToolCallStatus.FINISHED ) );
                return result;
            }
            catch ( TimeoutException e )
            {
                String message = "Tool call timed out: " + toolName;
                listener.onToolCallEvent( new ToolCallEvent(
                        id,
                        toolName,
                        toolInput,
                        message,
                        ToolCallStatus.FAILED ) );
                throw new RuntimeException( message, e );
            }
            catch ( Exception e )
            {
                String message = e.getCause() != null && e.getCause().getMessage() != null
                        ? e.getCause().getMessage()
                        : e.getMessage();
                listener.onToolCallEvent( new ToolCallEvent(
                        id,
                        toolName,
                        toolInput,
                        message,
                        ToolCallStatus.FAILED ) );
                if ( e instanceof RuntimeException runtime )
                {
                    throw runtime;
                }
                throw new RuntimeException( message, e );
            }
        }
    }
}
