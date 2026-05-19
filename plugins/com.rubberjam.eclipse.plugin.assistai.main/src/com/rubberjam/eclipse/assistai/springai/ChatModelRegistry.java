package com.rubberjam.eclipse.assistai.springai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptorRepository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;

@Creatable
@Singleton
public class ChatModelRegistry
{
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingModels = new ConcurrentHashMap<>();

    @Inject
    private ChatModelFactory factory;

    @Inject
    private ModelApiDescriptorRepository descriptorRepository;

    public ChatModel getModel(String uid) {
        return models.computeIfAbsent(uid, key -> {
            ModelApiDescriptor descriptor = descriptorRepository.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + key));
            return factory.createChatModel(descriptor);
        });
    }

    public StreamingChatModel getStreamingModel(String uid) {
        return streamingModels.computeIfAbsent(uid, key -> {
            ModelApiDescriptor descriptor = descriptorRepository.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + key));
            return factory.createStreamingChatModel(descriptor);
        });
    }

    public void invalidate(String uid) {
        models.remove(uid);
        streamingModels.remove(uid);
    }

    public void invalidateAll() {
        models.clear();
        streamingModels.clear();
    }
}
