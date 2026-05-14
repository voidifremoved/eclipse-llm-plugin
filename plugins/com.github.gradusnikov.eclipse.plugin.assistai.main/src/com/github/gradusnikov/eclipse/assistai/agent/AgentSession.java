package com.github.gradusnikov.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;

import com.github.gradusnikov.eclipse.assistai.chat.Attachment;
import com.github.gradusnikov.eclipse.assistai.chat.ChatMessage;
import com.github.gradusnikov.eclipse.assistai.models.ModelApiDescriptor;

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
                .defaultTools(toolBridge.getToolCallbacks())
                .build();
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

        return chatClient.prompt()
                .messages(conversationHistory)
                .stream()
                .chatResponse();
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
}
