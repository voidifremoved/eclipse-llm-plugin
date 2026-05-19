package com.rubberjam.eclipse.assistai.agent;

import java.util.List;

import com.rubberjam.eclipse.assistai.chat.Attachment;
import com.rubberjam.eclipse.assistai.chat.ChatMessage;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;
import com.rubberjam.eclipse.assistai.springai.AgentChatSession;
import com.rubberjam.eclipse.assistai.springai.AgentStreamChunk;

import reactor.core.publisher.Flux;

/**
 * Agent tab session facade; Spring AI integration is delegated to {@link AgentChatSession}.
 */
public class AgentSession
{
    private final AgentChatSession chatSession;

    public AgentSession( AgentChatSession chatSession )
    {
        this.chatSession = chatSession;
    }

    public void initialize( ModelApiDescriptor model )
    {
        chatSession.initialize( model );
    }

    public void setToolCallEventListener( ToolCallEventListener listener )
    {
        chatSession.setToolCallEventListener( listener );
    }

    public String getCurrentModelUid()
    {
        return chatSession.getCurrentModelUid();
    }

    public Flux<AgentStreamChunk> sendMessage( String text, List<Attachment> attachments )
    {
        return chatSession.sendMessage( text, attachments );
    }

    public Flux<AgentStreamChunk> sendMessage( String text, List<Attachment> attachments, String messageId )
    {
        return chatSession.sendMessage( text, attachments, messageId );
    }

    public void appendAssistantResponse( String messageId, String responseText )
    {
        chatSession.appendAssistantResponse( messageId, responseText );
    }

    public void appendToolMessage( String messageId, String toolName, String content )
    {
        chatSession.appendToolMessage( messageId, toolName, content );
    }

    public void updateSystemPrompt( String newSystemPrompt )
    {
        chatSession.updateSystemPrompt( newSystemPrompt );
    }

    public void updateMessageContent( String messageId, String content )
    {
        chatSession.updateMessageContent( messageId, content );
    }

    public void switchModel( ModelApiDescriptor newModel )
    {
        chatSession.switchModel( newModel );
    }

    public void clear()
    {
        chatSession.clear();
    }

    public void restoreMessages( List<AgentMessageSnapshot> messages )
    {
        chatSession.restoreMessages( messages );
    }

    public List<AgentMessageSnapshot> snapshotMessages()
    {
        return chatSession.snapshotMessages();
    }

    public void removeLastMessage()
    {
        chatSession.removeLastMessage();
    }

    public void removeMessageById( String id )
    {
        chatSession.removeMessageById( id );
    }

    public List<ChatMessage> getHistory()
    {
        return chatSession.getHistory();
    }

    public String getSessionId()
    {
        return chatSession.getSessionId();
    }
}
