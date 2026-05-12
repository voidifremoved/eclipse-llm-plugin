package com.github.gradusnikov.eclipse.assistai.agent;

import java.net.URI;
import java.util.Objects;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.anthropic.client.AnthropicClient;
import com.github.gradusnikov.eclipse.assistai.models.ModelApiDescriptor;

import io.micrometer.observation.ObservationRegistry;
import jakarta.inject.Singleton;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;

/**
 * Builds Spring AI {@link ChatModel} instances from persisted {@link ModelApiDescriptor}
 * rows, using the same URL heuristics as {@code AbstractLanguageModelHttpClientProvider}.
 */
@Creatable
@Singleton
public class ChatModelFactory
{

    /**
     * Creates a chat model for the given descriptor.
     *
     * @throws IllegalStateException when the URL points to an API that Spring AI cannot
     *             drive yet (native Gemini REST, OpenAI Responses, and so on).
     */
    public ChatModel createChatModel( ModelApiDescriptor descriptor )
    {
        Objects.requireNonNull( descriptor, "descriptor" );
        String apiUrl = descriptor.apiUrl();
        String lower = apiUrl.toLowerCase();

        if ( lower.contains( "/v1/responses" ) )
        {
            throw new IllegalStateException(
                    "OpenAI Responses API URLs are not supported by the Spring AI agent path yet; "
                            + "use a /v1/chat/completions URL or the legacy chat client." );
        }

        if ( lower.contains( "googleapis" ) )
        {
            if ( lower.contains( "/openai/" ) || lower.contains( "openai.chat" ) )
            {
                return createOpenAiCompatibleChatModel( descriptor );
            }
            throw new IllegalStateException(
                    "Native Google Gemini URLs (generativelanguage / Vertex-style googleapis) are not "
                            + "supported by the Spring AI agent path yet; use a Gemini OpenAI-compatible "
                            + "base URL or the legacy chat client." );
        }

        if ( lower.contains( "anthropic" ) )
        {
            return createAnthropicChatModel( descriptor );
        }

        return createOpenAiCompatibleChatModel( descriptor );
    }

    private static ChatModel createOpenAiCompatibleChatModel( ModelApiDescriptor descriptor )
    {
        OpenAiEndpointParts parts = parseOpenAiStyleEndpoint( descriptor.apiUrl() );

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl( parts.baseUrl() )
                .apiKey( descriptor.apiKey() )
                .completionsPath( parts.completionsPath() )
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model( descriptor.modelName() );
        applyTemperature( descriptor, optionsBuilder );

        return OpenAiChatModel.builder()
                .openAiApi( openAiApi )
                .defaultOptions( optionsBuilder.build() )
                .toolCallingManager( ToolCallingManager.builder().build() )
                .retryTemplate( RetryUtils.DEFAULT_RETRY_TEMPLATE )
                .observationRegistry( ObservationRegistry.NOOP )
                .build();
    }

    private static ChatModel createAnthropicChatModel( ModelApiDescriptor descriptor )
    {
        String baseUrl = extractHttpOrigin( descriptor.apiUrl() );
        AnthropicClient client = AnthropicSetup.setupSyncClient(
                baseUrl,
                descriptor.apiKey(),
                descriptor.requestTimeout(),
                null,
                null,
                null );

        AnthropicChatOptions.Builder optionsBuilder = AnthropicChatOptions.builder()
                .model( descriptor.modelName() );
        applyTemperature( descriptor, optionsBuilder );

        return AnthropicChatModel.builder()
                .anthropicClient( client )
                .options( optionsBuilder.build() )
                .toolCallingManager( ToolCallingManager.builder().build() )
                .observationRegistry( ObservationRegistry.NOOP )
                .build();
    }

    private static void applyTemperature( ModelApiDescriptor descriptor, OpenAiChatOptions.Builder optionsBuilder )
    {
        if ( descriptor.scaledTemperature().isPresent() )
        {
            float t = descriptor.scaledTemperature().get().floatValue();
            optionsBuilder.temperature( Double.valueOf( t ) );
        }
    }

    private static void applyTemperature( ModelApiDescriptor descriptor, AnthropicChatOptions.Builder optionsBuilder )
    {
        if ( descriptor.scaledTemperature().isPresent() )
        {
            float t = descriptor.scaledTemperature().get().floatValue();
            optionsBuilder.temperature( Double.valueOf( t ) );
        }
    }

    private static String extractHttpOrigin( String apiUrl )
    {
        URI uri = URI.create( apiUrl.trim() );
        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        if ( scheme == null || authority == null )
        {
            throw new IllegalStateException( "Invalid API URL (missing scheme or host): " + apiUrl );
        }
        return scheme + "://" + authority;
    }

    private static OpenAiEndpointParts parseOpenAiStyleEndpoint( String apiUrl )
    {
        URI uri = URI.create( apiUrl.trim() );
        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        if ( scheme == null || authority == null )
        {
            throw new IllegalStateException( "Invalid API URL (missing scheme or host): " + apiUrl );
        }
        String baseUrl = scheme + "://" + authority;
        String path = uri.getPath();
        if ( path == null || path.isEmpty() || "/".equals( path ) )
        {
            path = "/v1/chat/completions";
        }
        if ( !path.startsWith( "/" ) )
        {
            path = "/" + path;
        }
        return new OpenAiEndpointParts( baseUrl, path );
    }

    private record OpenAiEndpointParts( String baseUrl, String completionsPath )
    {
    }
}
