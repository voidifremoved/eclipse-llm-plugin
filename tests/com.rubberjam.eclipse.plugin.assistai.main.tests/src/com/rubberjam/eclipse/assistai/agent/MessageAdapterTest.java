package com.rubberjam.eclipse.assistai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.rubberjam.eclipse.assistai.chat.ChatMessage;

/**
 * Tests {@link MessageAdapter} without referencing Spring AI types directly, so the test
 * fragment does not need {@code org.springframework.ai.content} on its compile classpath.
 */
public class MessageAdapterTest
{

    @Test
    public void testToSpringAiConvertsUserMessageCorrectly()
    {
        ChatMessage chatMessage = new ChatMessage( "id-123", "user" );
        chatMessage.setContent( "Hello AI" );

        ChatMessage convertedBack = MessageAdapter.fromSpringAi( MessageAdapter.toSpringAi( chatMessage ) );

        assertNotNull( convertedBack );
        assertEquals( "user", convertedBack.getRole() );
        assertEquals( "Hello AI", convertedBack.getContent() );
    }

    @Test
    public void testToSpringAiConvertsAssistantMessageCorrectly()
    {
        ChatMessage chatMessage = new ChatMessage( "id-789", "assistant" );
        chatMessage.setContent( "Hello Human" );

        ChatMessage convertedBack = MessageAdapter.fromSpringAi( MessageAdapter.toSpringAi( chatMessage ) );

        assertEquals( "assistant", convertedBack.getRole() );
        assertEquals( "Hello Human", convertedBack.getContent() );
    }

    @Test
    public void testFromSpringAiConvertsToChatMessage()
    {
        ChatMessage original = new ChatMessage( "id-456", "assistant" );
        original.setContent( "Hello Human" );

        ChatMessage convertedBack = MessageAdapter.fromSpringAi( MessageAdapter.toSpringAi( original ) );

        assertNotNull( convertedBack );
        assertEquals( "assistant", convertedBack.getRole() );
        assertEquals( "Hello Human", convertedBack.getContent() );
    }
}
