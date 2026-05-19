package com.rubberjam.eclipse.assistai.agent.provider;

import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;

/**
 * OpenAI-compatible APIs (OpenAI, Mistral, Groq, Grok, DeepSeek, etc.).
 */
public final class OpenAiCompatibleChatModelProvider implements ChatModelProvider
{
    public static final OpenAiCompatibleChatModelProvider INSTANCE = new OpenAiCompatibleChatModelProvider();

    private OpenAiCompatibleChatModelProvider()
    {
    }

    @Override
    public ChatModel create( ModelApiDescriptor descriptor )
    {
        String baseUrl = toBaseUrl( descriptor.apiUrl() );

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .baseUrl( baseUrl )
            .apiKey( descriptor.apiKey() )
            .model( descriptor.modelName() )
            .temperature( descriptor.scaledTemperature().map( Float::doubleValue ).orElse( null ) )
            .build();

        return OpenAiChatModel.builder()
            .openAiClient( OpenAiSetup.setupSyncClient(
                options.getBaseUrl(),
                options.getApiKey(),
                options.getCredential(),
                options.getMicrosoftDeploymentName(),
                options.getMicrosoftFoundryServiceVersion(),
                options.getOrganizationId(),
                options.isMicrosoftFoundry(),
                options.isGitHubModels(),
                options.getModel(),
                options.getTimeout(),
                options.getMaxRetries(),
                options.getProxy(),
                options.getCustomHeaders() ) )
            .options( options )
            .build();
    }

    /**
     * {@link OpenAiSetup} configures the official OpenAI Java SDK base URL.
     * Descriptors often store the full path for legacy HTTP clients.
     */
    private static String toBaseUrl( String apiUrl )
    {
        if ( apiUrl == null || apiUrl.isBlank() )
        {
            return apiUrl;
        }
        String base = apiUrl.trim();
        while ( base.endsWith( "/" ) )
        {
            base = base.substring( 0, base.length() - 1 );
        }
        if ( base.endsWith( "/v1/chat/completions" ) )
        {
            return base.substring( 0, base.length() - "/v1/chat/completions".length() );
        }
        if ( base.endsWith( "/chat/completions" ) )
        {
            return base.substring( 0, base.length() - "/chat/completions".length() );
        }
        return base;
    }
}
