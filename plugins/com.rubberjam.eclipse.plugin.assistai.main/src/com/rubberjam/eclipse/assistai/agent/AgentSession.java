package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;

import com.rubberjam.eclipse.assistai.springai.ChatModelRegistry;
import com.rubberjam.eclipse.assistai.springai.MessageAdapter;
import com.rubberjam.eclipse.assistai.chat.Attachment;
import com.rubberjam.eclipse.assistai.chat.ChatMessage;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;

import reactor.core.publisher.Flux;

public class AgentSession
{
    private final String sessionId;
    private ChatClient chatClient;
    private ModelApiDescriptor currentModel;
    private final McpToolBridge toolBridge;
    private final ChatModelRegistry modelRegistry;
    private final List<org.springframework.ai.chat.messages.Message> conversationHistory;
    private final List<ChatMessage> displayHistory;
    private final String systemPrompt;
    private ToolCallEventListener toolCallEventListener = ToolCallEventListener.noop();

    public AgentSession(ChatModelRegistry modelRegistry, McpToolBridge toolBridge, String systemPrompt)
    {
        this.sessionId = UUID.randomUUID().toString();
        this.modelRegistry = modelRegistry;
        this.toolBridge = toolBridge;
        this.conversationHistory = new ArrayList<>();
        this.displayHistory = new ArrayList<>();
        this.systemPrompt = systemPrompt;

        this.conversationHistory.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));
    }

    public void initialize(ModelApiDescriptor model)
    {
        this.currentModel = model;
        ChatModel chatModel = modelRegistry.getModel(model.uid());
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(toolBridge.getToolCallbacks( toolCallEventListener ))
                .build();
    }

    public void setToolCallEventListener( ToolCallEventListener listener )
    {
        this.toolCallEventListener = listener != null ? listener : ToolCallEventListener.noop();
        if ( currentModel != null )
        {
            initialize( currentModel );
        }
    }

    public String getCurrentModelUid()
    {
        return currentModel != null ? currentModel.uid() : null;
    }

    public Flux<ChatResponse> sendMessage(String text, List<Attachment> attachments)
    {
        return sendMessage( text, attachments, UUID.randomUUID().toString() );
    }

    public Flux<ChatResponse> sendMessage( String text, List<Attachment> attachments, String messageId )
    {
        if (chatClient == null) {
            throw new IllegalStateException("AgentSession not initialized with a model.");
        }

        ChatMessage userChatMsg = new ChatMessage(messageId, "user");
        userChatMsg.setContent(text);
        if (attachments != null) {
            userChatMsg.setAttachments(attachments);
        }

        org.springframework.ai.chat.messages.Message userMessage = MessageAdapter.toSpringAi(userChatMsg);
        conversationHistory.add(userMessage);
        displayHistory.add( userChatMsg );

        return chatClient.prompt()
                .messages(conversationHistory)
                .stream()
                .chatResponse();
    }

    public void appendAssistantResponse(org.springframework.ai.chat.messages.Message assistantMessage) {
        conversationHistory.add(assistantMessage);
    }

    public void appendAssistantResponse(String responseText) {
        appendAssistantResponse( UUID.randomUUID().toString(), responseText );
    }

    public void appendAssistantResponse( String messageId, String responseText ) {
        ChatMessage msg = new ChatMessage(messageId, "assistant");
        msg.setContent(responseText);
        conversationHistory.add(MessageAdapter.toSpringAi(msg));
        displayHistory.add( msg );
    }

    public void appendToolMessage( String messageId, String content )
    {
        ChatMessage msg = new ChatMessage( messageId, "tool" );
        msg.setContent( content );
        displayHistory.add( msg );
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

    public void switchModel(ModelApiDescriptor newModel)
    {
        if (!newModel.equals(this.currentModel)) {
            initialize(newModel);
        }
    }

    public void clear()
    {
        conversationHistory.clear();
        displayHistory.clear();
        conversationHistory.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));
    }

    public void restoreMessages( List<AgentMessageSnapshot> messages )
    {
        conversationHistory.clear();
        displayHistory.clear();
        conversationHistory.add( new org.springframework.ai.chat.messages.SystemMessage( systemPrompt ) );
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

    public void removeLastMessage() {
        if (!displayHistory.isEmpty()) {
            displayHistory.remove(displayHistory.size() - 1);
        }
        rebuildConversationHistory();
    }

    public void removeMessageById(String id) {
        displayHistory.removeIf( msg -> id.equals( msg.getId() ) );
        rebuildConversationHistory();
    }

    public List<ChatMessage> getHistory() {
        return getDisplayHistorySnapshot();
    }

    public String getSessionId()
    {
        return sessionId;
    }

    private void rebuildConversationHistory()
    {
        conversationHistory.clear();
        conversationHistory.add( new org.springframework.ai.chat.messages.SystemMessage( systemPrompt ) );
        for ( ChatMessage message : displayHistory )
        {
            if ( !"tool".equals( message.getRole() ) )
            {
                conversationHistory.add( MessageAdapter.toSpringAi( message ) );
            }
        }
    }

    private List<ChatMessage> getDisplayHistorySnapshot()
    {
        if ( !displayHistory.isEmpty() )
        {
            return new ArrayList<>( displayHistory );
        }
        List<ChatMessage> fallback = new ArrayList<>();
        for ( org.springframework.ai.chat.messages.Message message : conversationHistory )
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
