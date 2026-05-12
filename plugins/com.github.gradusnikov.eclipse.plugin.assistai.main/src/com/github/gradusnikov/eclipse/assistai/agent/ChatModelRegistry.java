package com.github.gradusnikov.eclipse.assistai.agent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.github.gradusnikov.eclipse.assistai.models.ModelApiDescriptor;
import com.github.gradusnikov.eclipse.assistai.models.ModelApiDescriptorRepository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Caches {@link ChatModel} instances keyed by {@link ModelApiDescriptor#uid()}. Call
 * {@link #invalidate(String)} or {@link #invalidateAll()} after model preferences change.
 */
@Creatable
@Singleton
public class ChatModelRegistry
{
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();

    @Inject
    private ChatModelFactory factory;

    @Inject
    private ModelApiDescriptorRepository descriptorRepository;

    public ChatModel getModel( String uid )
    {
        Objects.requireNonNull( uid, "uid" );
        return models.computeIfAbsent( uid, key -> {
            ModelApiDescriptor descriptor = descriptorRepository.findById( key ).orElseThrow(
                    () -> new IllegalArgumentException( "Unknown model uid: " + key ) );
            return factory.createChatModel( descriptor );
        } );
    }

    public void invalidate( String uid )
    {
        Objects.requireNonNull( uid, "uid" );
        models.remove( uid );
    }

    public void invalidateAll()
    {
        models.clear();
    }
}
