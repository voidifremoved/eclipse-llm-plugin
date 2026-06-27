package com.rubberjam.eclipse.assistai.completion;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.chat.Conversation;

import jakarta.inject.Inject;

/**
 * Streaming code completion via Spring AI ({@link SpringAiStreamingCompletionClient}).
 */
@Creatable
public class StreamingCompletionClient
{
    private final SpringAiStreamingCompletionClient springAiClient;
    private final CompletionConfiguration configuration;
    private final ILog logger;

    @Inject
    public StreamingCompletionClient(
            SpringAiStreamingCompletionClient springAiClient,
            CompletionConfiguration configuration,
            ILog logger )
    {
        this.springAiClient = Objects.requireNonNull( springAiClient );
        this.configuration = Objects.requireNonNull( configuration );
        this.logger = Objects.requireNonNull( logger );
    }

    /**
     * @param conversation The conversation/prompt to send
     * @param onChunk Called for each chunk of text received (optional, can be null)
     * @return A future that completes with the full response text
     */
    public CompletableFuture<String> startStreaming(
            Conversation conversation,
            Consumer<String> onChunk )
    {
        if ( !configuration.isEnabled() )
        {
            logger.info( "LLM code completion is disabled in preferences" );
            return CompletableFuture.completedFuture( "" );
        }
        return springAiClient.startStreaming( conversation, onChunk );
    }
}
