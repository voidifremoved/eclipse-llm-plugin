package com.rubberjam.eclipse.assistai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import org.springframework.ai.chat.messages.Message;

import com.rubberjam.eclipse.assistai.chat.ChatMessage;

public class MessageAdapterTest {

    @Test
    public void testToSpringAiConvertsUserMessageCorrectly() throws Exception {
        ChatMessage chatMessage = new ChatMessage("id-123", "user");
        chatMessage.setContent("Hello AI");

        Message springAiMessage = MessageAdapter.toSpringAi(chatMessage);

        assertNotNull(springAiMessage);
        assertEquals("USER", springAiMessage.getMessageType().getValue());

        // We use reflection on Object to avoid OSGi visibility issues with Content class
        Object obj = springAiMessage;
        Method getText;
        try {
            getText = obj.getClass().getMethod("getText");
        } catch (NoSuchMethodException e) {
            getText = obj.getClass().getMethod("getContent");
        }
        assertEquals("Hello AI", (String) getText.invoke(obj));
    }

    @Test
    public void testFromSpringAiConvertsToChatMessage() {
        ChatMessage original = new ChatMessage("id-456", "assistant");
        original.setContent("Hello Human");

        Message springAiMessage = MessageAdapter.toSpringAi(original);
        ChatMessage convertedBack = MessageAdapter.fromSpringAi(springAiMessage);

        assertNotNull(convertedBack);
        assertEquals("assistant", convertedBack.getRole());
        assertEquals("Hello Human", convertedBack.getContent());
    }
}
