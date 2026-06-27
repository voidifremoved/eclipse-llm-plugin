package com.rubberjam.eclipse.assistai.springai.provider;

import com.anthropic.models.messages.Model;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Anthropic Claude models via the official Anthropic SDK (Spring AI {@link AnthropicChatModel}).
 */
public final class AnthropicChatModelProvider implements ChatModelProvider
{
    public static final AnthropicChatModelProvider INSTANCE = new AnthropicChatModelProvider();

    private AnthropicChatModelProvider()
    {
    }

    @Override
    public ChatModel create( ModelApiDescriptor descriptor )
    {
        AnthropicChatOptions.Builder optionsBuilder = AnthropicChatOptions.builder()
            .apiKey( descriptor.apiKey() )
            .model( Model.of( descriptor.modelName() ) );
        descriptor.scaledTemperature().ifPresent( t -> optionsBuilder.temperature( t.doubleValue() ) );
        AnthropicChatOptions options = optionsBuilder.build();

        return AnthropicChatModel.builder()
            .anthropicClient( AnthropicSetup.setupSyncClient(
                options.getBaseUrl(),
                options.getApiKey(),
                null,
                null,
                null,
                null ) )
            .options( options )
            .build();
    }
}
