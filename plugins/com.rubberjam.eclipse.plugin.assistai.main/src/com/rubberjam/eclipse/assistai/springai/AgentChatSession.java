package com.rubberjam.eclipse.assistai.springai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import com.rubberjam.eclipse.assistai.agent.AgentMessageSnapshot;
import com.rubberjam.eclipse.assistai.agent.AgentToolPolicy;
import com.rubberjam.eclipse.assistai.agent.ToolCallEventListener;
import com.rubberjam.eclipse.assistai.chat.Attachment;
import com.rubberjam.eclipse.assistai.chat.ChatMessage;
import com.rubberjam.eclipse.assistai.chat.Conversation;
import com.rubberjam.eclipse.assistai.chat.ConversationContext;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;

import reactor.core.publisher.Flux;

/**
 * Spring AI chat session for the agent UI. All {@code org.springframework.ai} usage for agent chat lives here.
 */
public class AgentChatSession
{
    private final String sessionId;
    private ChatClient chatClient;
    private ModelApiDescriptor currentModel;
    private final McpToolBridge toolBridge;

    private final AgentToolPolicy agentToolPolicy;

    private final ChatModelRegistry modelRegistry;
    private final List<Message> conversationHistory;
    private final List<ChatMessage> displayHistory;
    private String systemPrompt;
    private ToolCallEventListener toolCallEventListener = ToolCallEventListener.noop();

    private AgentSendOptions currentSendOptions = AgentSendOptions.DEFAULT;

    public AgentChatSession(
            ChatModelRegistry modelRegistry,
            McpToolBridge toolBridge,
            AgentToolPolicy agentToolPolicy,
            String systemPrompt )
    {
        this.sessionId = UUID.randomUUID().toString();
        this.modelRegistry = modelRegistry;
        this.toolBridge = toolBridge;
        this.agentToolPolicy = agentToolPolicy;
        this.conversationHistory = new ArrayList<>();
        this.displayHistory = new ArrayList<>();
        this.systemPrompt = systemPrompt;
    }

    public void initialize( ModelApiDescriptor model )
    {
        this.currentModel = model;
        rebuildChatClient();
    }

    public void setToolCallEventListener( ToolCallEventListener listener )
    {
        this.toolCallEventListener = listener != null ? listener : ToolCallEventListener.noop();
        if ( currentModel != null )
        {
            rebuildChatClient();
        }
    }

    public String getCurrentModelUid()
    {
        return currentModel != null ? currentModel.uid() : null;
    }

    public Flux<AgentStreamChunk> sendMessage( String text, List<Attachment> attachments )
    {
        return sendMessage( text, attachments, UUID.randomUUID().toString() );
    }

    public Flux<AgentStreamChunk> sendMessage( String text, List<Attachment> attachments, String messageId )
    {
        return sendMessage( text, attachments, messageId, AgentSendOptions.DEFAULT );
    }

    public Flux<AgentStreamChunk> sendMessage(
            String text,
            List<Attachment> attachments,
            String messageId,
            AgentSendOptions sendOptions )
    {
        if ( currentModel == null )
        {
            throw new IllegalStateException( "Agent chat session not initialized with a model." );
        }
        currentSendOptions = sendOptions != null ? sendOptions : AgentSendOptions.DEFAULT;
        rebuildConversationHistory();
        rebuildChatClient();

        ChatMessage userChatMsg = new ChatMessage( messageId, "user" );
        userChatMsg.setContent( text );
        if ( attachments != null )
        {
            userChatMsg.setAttachments( attachments );
        }

        Message userMessage = MessageAdapter.toSpringAi( userChatMsg );
        conversationHistory.add( userMessage );
        displayHistory.add( userChatMsg );

        return chatClient.prompt()
                .messages( conversationHistory )
                .stream()
                .chatResponse()
                .map( this::toStreamChunk );
    }

    public void appendAssistantResponse( String messageId, String responseText )
    {
        ChatMessage msg = new ChatMessage( messageId, "assistant" );
        msg.setContent( responseText );
        conversationHistory.add( MessageAdapter.toSpringAi( msg ) );
        displayHistory.add( msg );
    }

    public void appendToolMessage( String messageId, String toolName, String content )
    {
        ChatMessage msg = new ChatMessage( messageId, toolName, "tool" );
        msg.setContent( content );
        displayHistory.add( msg );
    }

    public void updateSystemPrompt( String newSystemPrompt )
    {
        if ( newSystemPrompt == null )
        {
            return;
        }
        this.systemPrompt = newSystemPrompt;
        rebuildConversationHistory();
        if ( currentModel != null )
        {
            rebuildChatClient();
        }
    }

    public void updateMessageContent( String messageId, String content )
    {
        for ( ChatMessage message : displayHistory )
        {
            if ( messageId.equals( message.getId() ) )
            {
                message.setContent( content );
                return;
            }
        }
    }

    public void switchModel( ModelApiDescriptor newModel )
    {
        this.currentModel = newModel;
        rebuildChatClient();
    }

    public void clear()
    {
        conversationHistory.clear();
        displayHistory.clear();
    }

    public void restoreMessages( List<AgentMessageSnapshot> messages )
    {
        conversationHistory.clear();
        displayHistory.clear();
        if ( messages == null )
        {
            return;
        }
        for ( AgentMessageSnapshot message : messages )
        {
            if ( message == null || "system".equals( message.role() ) )
            {
                continue;
            }
            ChatMessage chatMessage = new ChatMessage( message.id(), message.role() );
            chatMessage.setContent( message.content() != null ? message.content() : "" );
            displayHistory.add( chatMessage );
        }
        rebuildConversationHistory();
    }

    public List<AgentMessageSnapshot> snapshotMessages()
    {
        List<AgentMessageSnapshot> messages = new ArrayList<>();
        for ( ChatMessage chatMessage : getDisplayHistorySnapshot() )
        {
            if ( "system".equals( chatMessage.getRole() ) )
            {
                continue;
            }
            messages.add( new AgentMessageSnapshot(
                    chatMessage.getId(),
                    chatMessage.getRole(),
                    chatMessage.getContent() ) );
        }
        return messages;
    }

    public void removeLastMessage()
    {
        if ( !displayHistory.isEmpty() )
        {
            displayHistory.remove( displayHistory.size() - 1 );
        }
        rebuildConversationHistory();
    }

    public void removeMessageById( String id )
    {
        displayHistory.removeIf( msg -> id.equals( msg.getId() ) );
        rebuildConversationHistory();
    }

    public List<ChatMessage> getHistory()
    {
        return getDisplayHistorySnapshot();
    }

    public String getSessionId()
    {
        return sessionId;
    }

    private void rebuildChatClient()
    {
        if ( currentModel == null )
        {
            return;
        }
        ChatModel chatModel = modelRegistry.getModel( currentModel.uid() );
        ChatClient.Builder builder = ChatClient.builder( chatModel )
                .defaultSystem( systemPrompt );
        if ( currentModel.functionCalling() && currentSendOptions.isToolsEnabled() )
        {
            java.util.Set<String> allowedTools = currentSendOptions.getAllowedToolsOverride();
            if ( allowedTools == null )
            {
                allowedTools = agentToolPolicy.resolveAllowedToolNames();
            }
            ConversationContext toolContext = ConversationContext.builder()
                    .contextId( "agent-" + sessionId )
                    .conversation( new Conversation() )
                    .allowedTools( allowedTools )
                    .maxToolCalls( agentToolPolicy.getMaxToolRounds() )
                    .build();
            builder.defaultToolCallbacks( toolBridge.getToolCallbacks( toolContext, toolCallEventListener ) );
        }
        this.chatClient = builder.build();
    }

    private AgentStreamChunk toStreamChunk( ChatResponse chatResponse )
    {
        Generation generation = chatResponse.getResult();
        AssistantMessage output = generation.getOutput();
        String content = output.getText();
        String thinking = extractReasoningText( output );
        if ( thinking != null && !thinking.isEmpty() )
        {
            return new AgentStreamChunk(
                    content != null ? content : "",
                    thinking );
        }
        return new AgentStreamChunk( content != null ? content : "" );
    }

    private static String extractReasoningText( AssistantMessage output )
    {
        return metadataString( output.getMetadata(), "reasoningContent", "reasoning_content", "thinking" );
    }

    private static String metadataString( Map<String, Object> metadata, String... keys )
    {
        if ( metadata == null )
        {
            return null;
        }
        for ( String key : keys )
        {
            Object value = metadata.get( key );
            if ( value instanceof String text && !text.isEmpty() )
            {
                return text;
            }
        }
        return null;
    }

    private void rebuildConversationHistory()
    {
        conversationHistory.clear();
        // System prompt is applied via ChatClient.defaultSystem — do not duplicate as a message.
        for ( ChatMessage message : displayHistory )
        {
            if ( "tool".equals( message.getRole() ) )
            {
                if ( isCompletedToolMessage( message.getContent() ) )
                {
                    conversationHistory.add( MessageAdapter.toSpringAi( message ) );
                }
            }
            else
            {
                conversationHistory.add( MessageAdapter.toSpringAi( message ) );
            }
        }
    }

    private static boolean isCompletedToolMessage( String content )
    {
        if ( content == null || content.isBlank() )
        {
            return false;
        }
        return content.contains( "**Status:** Finished" ) || content.contains( "**Status:** Failed" );
    }

    private List<ChatMessage> getDisplayHistorySnapshot()
    {
        if ( !displayHistory.isEmpty() )
        {
            return new ArrayList<>( displayHistory );
        }
        List<ChatMessage> fallback = new ArrayList<>();
        for ( Message message : conversationHistory )
        {
            ChatMessage chatMessage = MessageAdapter.fromSpringAi( message );
            if ( !"system".equals( chatMessage.getRole() ) )
            {
                fallback.add( chatMessage );
            }
        }
        return fallback;
    }
}
