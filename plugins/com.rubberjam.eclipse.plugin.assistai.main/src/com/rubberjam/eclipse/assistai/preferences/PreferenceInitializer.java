package com.rubberjam.eclipse.assistai.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IPersistentPreferenceStore;

import java.util.UUID;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptorRepository;
import com.rubberjam.eclipse.assistai.prompt.PromptLoader;
import com.rubberjam.eclipse.assistai.prompt.Prompts;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Class used to initialize default preference values.
 */
@Creatable
@Singleton
public class PreferenceInitializer extends AbstractPreferenceInitializer
{
    @Inject
    private ModelApiDescriptorRepository modelApiDescriptorRepository;
    
    private McpServerRepository mcpServerRepository;
    
    public void init()
    {
        modelApiDescriptorRepository = Activator.getDefault().getModelApiDescriptorRepository();
        mcpServerRepository = Activator.getDefault().make( McpServerRepository.class );
    }
    
    public void initializeDefaultPreferences()
    {
        init();
        
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();

        ModelApiDescriptor openrouter = new ModelApiDescriptor(
                "openrouter-default-uid",
                "openai",
                "https://openrouter.ai/api/v1/chat/completions",
                "",
                10,
                30,
                "z-ai/glm-5.2",
                ModelApiDescriptor.TEMPERATURE_NOT_SUPPORTED,
                true,
                true );

        modelApiDescriptorRepository.initializeDefaultDescriptors( openrouter );
        modelApiDescriptorRepository.initializeDefaultDescriptorInUse( openrouter );
        
        // Built-in MCP servers are merged at load time; preferences only store user servers and overrides.
        store.setDefault( PreferenceConstants.ASSISTAI_DEFINED_MCP_SERVERS, "[]" );

        // Initialize HTTP MCP Server defaults
        store.setDefault(PreferenceConstants.ASSISTAI_MCP_HTTP_HOSTNAME, "localhost");
        store.setDefault(PreferenceConstants.ASSISTAI_MCP_HTTP_PORT, 27417);
        store.setDefault(PreferenceConstants.ASSISTAI_MCP_HTTP_ENABLED, true);
        // Generate auth token once and persist it — using setDefault(randomUUID) would
        // produce a new token on every Eclipse restart since defaults are not persisted.
        if ( store.getString(PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN).isBlank() )
        {
            store.setValue(PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN, UUID.randomUUID().toString());
            if (store instanceof IPersistentPreferenceStore persistentStore)
            {
                try {
                    persistentStore.save();
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Initialize Code Completion defaults
        store.setDefault(PreferenceConstants.ASSISTAI_COMPLETION_ENABLED, true);
        store.setDefault(PreferenceConstants.ASSISTAI_COMPLETION_MODEL, ""); // Empty means use chat model
        store.setDefault(PreferenceConstants.ASSISTAI_COMPLETION_TIMEOUT_SECONDS, 8);
        store.setDefault(PreferenceConstants.ASSISTAI_COMPLETION_HOTKEY, PreferenceConstants.ASSISTAI_COMPLETION_HOTKEY_DEFAULT);

        // AI Ignore defaults
        store.setDefault(PreferenceConstants.ASSISTAI_IGNORE_FILENAME, ".aiignore");
        store.setDefault(PreferenceConstants.ASSISTAI_GLOBAL_EXCLUDE_PATTERNS, "*.pem\n*.key\n*.env\n.env.*\ncredentials.json\nsecrets.properties");

        store.setDefault( PreferenceConstants.ASSISTAI_AGENT_ALLOW_WEB_TOOLS, false );
        store.setDefault( PreferenceConstants.ASSISTAI_AGENT_USE_ECLIPSE_SKILLS, true );
        store.setDefault( PreferenceConstants.ASSISTAI_AGENT_VERIFY_AFTER_EDIT, true );
        store.setDefault( PreferenceConstants.ASSISTAI_AGENT_MAX_TOOL_ROUNDS, 25 );

        PromptLoader promptLoader = new PromptLoader();
        for ( Prompts prompt : Prompts.values() )
        {
            store.setDefault( prompt.preferenceName(), promptLoader.getDefaultPrompt( prompt.getFileName() ) );
        }
    }
    

}
