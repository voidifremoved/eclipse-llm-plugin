package com.rubberjam.eclipse.assistai.springai.provider;

import com.google.genai.Client;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

/**
 * Google Gemini Developer API (API key) via Spring AI {@link GoogleGenAiChatModel}.
 */
public final class GoogleGenAiChatModelProvider implements ChatModelProvider
{
    public static final GoogleGenAiChatModelProvider INSTANCE = new GoogleGenAiChatModelProvider();

    private GoogleGenAiChatModelProvider()
    {
    }

    @Override
    public ChatModel create( ModelApiDescriptor descriptor )
    {
        Client client = Client.builder()
            .apiKey( descriptor.apiKey() )
            .build();

        GoogleGenAiChatOptions.Builder optionsBuilder = GoogleGenAiChatOptions.builder()
            .model( descriptor.modelName() );
        descriptor.scaledTemperature().ifPresent( t -> optionsBuilder.temperature( t.doubleValue() ) );

        return GoogleGenAiChatModel.builder()
            .genAiClient( client )
            .options( optionsBuilder.build() )
            .build();
    }
}
