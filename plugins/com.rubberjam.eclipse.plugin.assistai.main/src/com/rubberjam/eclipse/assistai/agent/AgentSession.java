package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;

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
    private final String systemPrompt;
    private ToolCallEventListener toolCallEventListener = ToolCallEventListener.noop();

    public AgentSession(ChatModelRegistry modelRegistry, McpToolBridge toolBridge, String systemPrompt)
    {
        this.sessionId = UUID.randomUUID().toString();
        this.modelRegistry = modelRegistry;
        this.toolBridge = toolBridge;
        this.conversationHistory = new ArrayList<>();
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
        if (chatClient == null) {
            throw new IllegalStateException("AgentSession not initialized with a model.");
        }

        ChatMessage userChatMsg = new ChatMessage(UUID.randomUUID().toString(), "user");
        userChatMsg.setContent(text);
        if (attachments != null) {
            userChatMsg.setAttachments(attachments);
        }

        org.springframework.ai.chat.messages.Message userMessage = MessageAdapter.toSpringAi(userChatMsg);
        conversationHistory.add(userMessage);

        /*
         * Spring AI 2.0.0-M4 can mis-handle streaming tool-call loops with MCP
         * results, interpreting the tool result payload as a later tool name.
         * Keep the Flux contract for the presenter, but use the non-streaming path
         * so tool execution stays inside one stable ChatClient call.
         */
        return Flux.defer( () -> Flux.just( chatClient.prompt()
                .messages( conversationHistory )
                .call()
                .chatResponse() ) );
    }

    public void appendAssistantResponse(org.springframework.ai.chat.messages.Message assistantMessage) {
        conversationHistory.add(assistantMessage);
    }

    public void appendAssistantResponse(String responseText) {
        ChatMessage msg = new ChatMessage(UUID.randomUUID().toString(), "assistant");
        msg.setContent(responseText);
        conversationHistory.add(MessageAdapter.toSpringAi(msg));
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
        conversationHistory.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));
    }

    public void restoreMessages( List<AgentMessageSnapshot> messages )
    {
        conversationHistory.clear();
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
            conversationHistory.add( MessageAdapter.toSpringAi( chatMessage ) );
        }
    }

    public List<AgentMessageSnapshot> snapshotMessages()
    {
        List<AgentMessageSnapshot> messages = new ArrayList<>();
        for ( org.springframework.ai.chat.messages.Message message : conversationHistory )
        {
            ChatMessage chatMessage = MessageAdapter.fromSpringAi( message );
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
        if (!conversationHistory.isEmpty()) {
            conversationHistory.remove(conversationHistory.size() - 1);
        }
    }

    public void removeMessageById(String id) {
        // Find matching message by checking its adapted representation
        conversationHistory.removeIf(msg -> {
            ChatMessage chatMsg = MessageAdapter.fromSpringAi(msg);
            return id.equals(chatMsg.getId());
        });
    }

    public List<ChatMessage> getHistory() {
        return conversationHistory.stream()
            .map(MessageAdapter::fromSpringAi)
            .collect(Collectors.toList());
    }

    public String getSessionId()
    {
        return sessionId;
    }
}
