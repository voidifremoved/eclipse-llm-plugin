package com.github.gradusnikov.eclipse.assistai.agent;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.github.gradusnikov.eclipse.assistai.prompt.PromptRepository;
import com.github.gradusnikov.eclipse.assistai.prompt.Prompts;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceCache;
import com.github.gradusnikov.eclipse.assistai.resources.CachedResource;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Creatable
@Singleton
public class AgentSystemPromptBuilder
{
    @Inject private PromptRepository promptRepository;
    @Inject private ResourceCache resourceCache;

    public String buildSystemPrompt()
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append(promptRepository.getPrompt(Prompts.SYSTEM.name()));
        prompt.append("\n");
        prompt.append(buildResourceContext());
        return prompt.toString();
    }

    private String buildResourceContext()
    {
        StringBuilder ctx = new StringBuilder();
        if (resourceCache != null && !resourceCache.isEmpty()) {
            ctx.append("\n=== User Shared Context ===\n");
            for (CachedResource res : resourceCache.getAll().values()) {
                ctx.append(res.content()).append("\n");
            }
            ctx.append("===========================\n");
        }
        return ctx.toString();
    }
}
