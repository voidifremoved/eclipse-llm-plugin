package com.rubberjam.eclipse.assistai.agent;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptorRepository;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Creatable
@Singleton
public class AgentSessionManager
{
    @Inject private ChatModelRegistry modelRegistry;
    @Inject private Provider<McpToolBridge> toolBridgeProvider;
    @Inject private ModelApiDescriptorRepository modelRepository;
    @Inject private AgentSystemPromptBuilder promptBuilder;

    private AgentSession currentSession;
    private McpToolBridge toolBridge;

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
        currentSession = new AgentSession(modelRegistry, getToolBridge(), systemPrompt);

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

    private McpToolBridge getToolBridge()
    {
        if ( toolBridge == null )
        {
            toolBridge = toolBridgeProvider.get();
        }
        return toolBridge;
    }
}
