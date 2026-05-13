package com.github.gradusnikov.eclipse.assistai.agent;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.github.gradusnikov.eclipse.assistai.models.ModelApiDescriptor;
import com.github.gradusnikov.eclipse.assistai.models.ModelApiDescriptorRepository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Creatable
@Singleton
public class AgentSessionManager
{
    @Inject private ChatModelRegistry modelRegistry;
    @Inject private McpToolBridge toolBridge;
    @Inject private ModelApiDescriptorRepository modelRepository;
    @Inject private AgentSystemPromptBuilder promptBuilder;

    private AgentSession currentSession;

    public AgentSession getOrCreateSession()
    {
        if (currentSession == null)
        {
            currentSession = newSession();
        }
        return currentSession;
    }

    public AgentSession newSession()
    {
        String systemPrompt = promptBuilder.buildSystemPrompt();
        currentSession = new AgentSession(modelRegistry, toolBridge, systemPrompt);

        ModelApiDescriptor currentModel = modelRepository.getChatModelInUse();
        if (currentModel != null) {
            currentSession.initialize(currentModel);
        }
        return currentSession;
    }

    public void switchModel(String modelUid)
    {
        ModelApiDescriptor model = modelRepository.findById(modelUid)
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelUid));

        if (currentSession != null) {
            currentSession.switchModel(model);
        }
    }

    public void destroySession()
    {
        if (currentSession != null) {
            currentSession.clear();
            currentSession = null;
        }
    }
}
