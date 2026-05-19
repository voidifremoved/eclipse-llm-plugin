package com.rubberjam.eclipse.assistai.springai;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.springai.provider.AnthropicChatModelProvider;
import com.rubberjam.eclipse.assistai.springai.provider.ChatModelProvider;
import com.rubberjam.eclipse.assistai.springai.provider.GoogleGenAiChatModelProvider;
import com.rubberjam.eclipse.assistai.springai.provider.OpenAiCompatibleChatModelProvider;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;

import jakarta.inject.Singleton;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;

/**
 * Builds {@link ChatModel} instances for the agent workflow.
 * <p>
 * Callers depend only on {@link ChatModel} and
 * {@link org.springframework.ai.chat.client.ChatClient}.
 * Provider-specific types stay in {@code springai.provider}.
 */
@Creatable
@Singleton
public class ChatModelFactory
{
    public ChatModel createChatModel( ModelApiDescriptor descriptor )
    {
        return resolveProvider( descriptor ).create( descriptor );
    }

    public StreamingChatModel createStreamingChatModel( ModelApiDescriptor descriptor )
    {
        ChatModel model = createChatModel( descriptor );
        if ( model instanceof StreamingChatModel streaming )
        {
            return streaming;
        }
        throw new IllegalStateException(
                "Model does not support streaming: " + descriptor.modelName() );
    }

    private static ChatModelProvider resolveProvider( ModelApiDescriptor descriptor )
    {
        String apiType = descriptor.apiType() != null ? descriptor.apiType().toLowerCase() : "";
        String apiUrl = descriptor.apiUrl() != null ? descriptor.apiUrl().toLowerCase() : "";

        if ( "claude".equals( apiType ) || apiUrl.contains( "anthropic" ) )
        {
            return AnthropicChatModelProvider.INSTANCE;
        }
        if ( "gemini".equals( apiType ) || apiUrl.contains( "googleapis" ) )
        {
            return GoogleGenAiChatModelProvider.INSTANCE;
        }
        return OpenAiCompatibleChatModelProvider.INSTANCE;
    }
}
