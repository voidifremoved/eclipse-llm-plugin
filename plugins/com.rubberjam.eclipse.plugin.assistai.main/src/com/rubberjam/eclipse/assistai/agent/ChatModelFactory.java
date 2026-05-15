package com.rubberjam.eclipse.assistai.agent;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;

import jakarta.inject.Singleton;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

@Creatable
@Singleton
public class ChatModelFactory
{
    public ChatModel createChatModel(ModelApiDescriptor descriptor)
    {
        return (ChatModel) createModel(descriptor);
    }

    public StreamingChatModel createStreamingChatModel(ModelApiDescriptor descriptor)
    {
        return (StreamingChatModel) createModel(descriptor);
    }

    private Object createModel(ModelApiDescriptor descriptor)
    {
        String apiUrl = toOpenAiApiBaseUrl( descriptor.apiUrl() );

        var apiBuilder = OpenAiApi.builder()
            .baseUrl(apiUrl)
            .apiKey(descriptor.apiKey());

        return OpenAiChatModel.builder()
            .openAiApi(apiBuilder.build())
            .defaultOptions(OpenAiChatOptions.builder()
                .model(descriptor.modelName())
                .temperature(descriptor.scaledTemperature().map(Float::doubleValue).orElse(null))
                .build())
            .build();
    }

    /**
     * Spring AI {@link OpenAiApi} appends {@code /v1/chat/completions} to the base URL.
     * Model descriptors often store the full path for the legacy HTTP clients.
     */
    private static String toOpenAiApiBaseUrl( String apiUrl )
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
