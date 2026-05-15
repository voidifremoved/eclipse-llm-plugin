package com.rubberjam.eclipse.assistai.agent.provider;

import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Creates a provider-specific {@link ChatModel} from a {@link ModelApiDescriptor}.
 * Agent code uses only {@link ChatModel} / {@link org.springframework.ai.chat.client.ChatClient}.
 */
public interface ChatModelProvider
{
    ChatModel create( ModelApiDescriptor descriptor );
}
