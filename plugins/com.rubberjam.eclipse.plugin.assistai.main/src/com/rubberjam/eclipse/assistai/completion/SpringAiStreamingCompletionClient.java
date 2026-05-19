package com.rubberjam.eclipse.assistai.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;

import com.rubberjam.eclipse.assistai.agent.McpToolBridge;
import com.rubberjam.eclipse.assistai.chat.ChatMessage;
import com.rubberjam.eclipse.assistai.chat.Conversation;
import com.rubberjam.eclipse.assistai.chat.ConversationContext;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptorRepository;
import com.rubberjam.eclipse.assistai.springai.ChatModelRegistry;
import com.rubberjam.eclipse.assistai.springai.MessageAdapter;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

/**
 * Code completion via Spring AI {@link ChatClient} and the completions model preference.
 */
@Creatable
public class SpringAiStreamingCompletionClient
{
    private static final Set<String> COMPLETION_ALLOWED_TOOLS = Set.of(
        "eclipse-ide__getSource",
        "eclipse-ide__readProjectResource",
        "eclipse-ide__getProjectLayout",
        "eclipse-ide__getCurrentlyOpenedFile",
        "eclipse-ide__getEditorSelection",
        "eclipse-ide__getJavaDoc",
        "eclipse-ide__getCompilationErrors",
        "eclipse-ide__getMethodCallHierarchy",
        "memory__completion_meta"
    );

    private static final Executor COMPLETION_EXECUTOR = Executors.newCachedThreadPool( new ThreadFactory()
    {
        private final AtomicInteger threadNumber = new AtomicInteger( 1 );

        @Override
        public Thread newThread( Runnable runnable )
        {
            Thread thread = new Thread( runnable, "LLM-SpringAI-Completion-" + threadNumber.getAndIncrement() );
            thread.setDaemon( true );
            return thread;
        }
    } );

    private final ChatModelRegistry modelRegistry;
    private final ModelApiDescriptorRepository modelRepository;
    private final McpToolBridge toolBridge;
    private final CompletionConfiguration configuration;
    private final ILog logger;

    @Inject
    public SpringAiStreamingCompletionClient(
            ChatModelRegistry modelRegistry,
            ModelApiDescriptorRepository modelRepository,
            McpToolBridge toolBridge,
            CompletionConfiguration configuration,
            ILog logger )
    {
        this.modelRegistry = Objects.requireNonNull( modelRegistry );
        this.modelRepository = Objects.requireNonNull( modelRepository );
        this.toolBridge = Objects.requireNonNull( toolBridge );
        this.configuration = Objects.requireNonNull( configuration );
        this.logger = Objects.requireNonNull( logger );
    }

    public CompletableFuture<String> startStreaming( Conversation conversation, Consumer<String> onChunk )
    {
        if ( !configuration.isEnabled() )
        {
            return CompletableFuture.completedFuture( "" );
        }

        CompletableFuture<String> completionFuture = new CompletableFuture<>();

        CompletableFuture.runAsync( () -> runStreaming( conversation, onChunk, completionFuture ), COMPLETION_EXECUTOR );

        return completionFuture;
    }

    private void runStreaming(
            Conversation conversation,
            Consumer<String> onChunk,
            CompletableFuture<String> completionFuture )
    {
        try
        {
            ModelApiDescriptor descriptor = modelRepository.getCompletionsModelInUse();
            if ( descriptor == null )
            {
                completionFuture.completeExceptionally(
                        new IllegalStateException( "No completion model configured" ) );
                return;
            }

            ChatModel model = modelRegistry.getModel( descriptor.uid() );

            List<Message> messages = new ArrayList<>();
            for ( ChatMessage chatMessage : conversation.messages() )
            {
                messages.add( MessageAdapter.toSpringAi( chatMessage ) );
            }

            ConversationContext context = ConversationContext.builder()
                    .contextId( "springai-completion-" + System.currentTimeMillis() )
                    .conversation( conversation )
                    .allowedTools( COMPLETION_ALLOWED_TOOLS )
                    .build();

            ChatClient client = ChatClient.builder( model )
                    .defaultToolCallbacks( toolBridge.getToolCallbacks( context ) )
                    .build();

            StringBuilder fullResponse = new StringBuilder();
            AtomicBoolean markdownTruncated = new AtomicBoolean( false );

            Flux<ChatResponse> stream = client.prompt()
                    .messages( messages )
                    .stream()
                    .chatResponse()
                    .timeout( configuration.getTimeout() );

            stream.doOnNext( chatResponse ->
            {
                if ( completionFuture.isCancelled() || completionFuture.isDone() )
                {
                    return;
                }
                String content = chatResponse.getResult().getOutput().getText();
                if ( content == null || content.isEmpty() )
                {
                    return;
                }
                String chunk = CompletionChunkSanitizer.sanitize( content, markdownTruncated );
                fullResponse.append( chunk );
                if ( onChunk != null && !chunk.isEmpty() )
                {
                    safeCallback( () -> onChunk.accept( chunk ), "chunk callback" );
                }
            } ).blockLast();

            if ( !completionFuture.isDone() )
            {
                completionFuture.complete( fullResponse.toString() );
            }
        }
        catch ( Exception e )
        {
            logger.error( "Spring AI completion failed", e );
            if ( !completionFuture.isDone() )
            {
                completionFuture.completeExceptionally( e );
            }
        }
    }

    private void safeCallback( Runnable callback, String callbackName )
    {
        try
        {
            callback.run();
        }
        catch ( Exception e )
        {
            logger.warn( "Error in " + callbackName + ": " + e.getMessage() );
        }
    }
}
