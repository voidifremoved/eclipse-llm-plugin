package com.github.gradusnikov.eclipse.assistai.agent;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.github.gradusnikov.eclipse.assistai.models.ModelApiDescriptor;

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
        String apiUrl = descriptor.apiUrl();

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
}
