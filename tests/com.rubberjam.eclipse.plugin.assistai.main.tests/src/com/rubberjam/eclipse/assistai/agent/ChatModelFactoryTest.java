package com.rubberjam.eclipse.assistai.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;
import com.rubberjam.eclipse.assistai.springai.ChatModelFactory;

public class ChatModelFactoryTest {

    @Test
    public void testCreateChatModelReturnsNonNullForMockedDescriptor() {
        ChatModelFactory factory = new ChatModelFactory();
        ModelApiDescriptor descriptor = new ModelApiDescriptor(
            "test-uid",
            "openai",
            "https://api.openai.com/v1",
            "sk-test",
            10,
            30,
            "gpt-4",
            1.0f,
            false,
            false
        );
        org.springframework.ai.chat.model.ChatModel model = factory.createChatModel(descriptor);
        assertNotNull(model);
    }
}
