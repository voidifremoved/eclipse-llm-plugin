package com.rubberjam.eclipse.assistai.agent.provider;

import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
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
        AnthropicChatOptions options = AnthropicChatOptions.builder()
            .apiKey( descriptor.apiKey() )
            .temperature( descriptor.scaledTemperature().map( Float::doubleValue ).orElse( null ) )
            .build();
        options.setModel( descriptor.modelName() );

        return AnthropicChatModel.builder()
            .options( options )
            .build();
    }
}
