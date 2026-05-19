package com.rubberjam.eclipse.assistai.completion;

import java.time.Duration;

import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jface.preference.IPreferenceStore;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.preferences.PreferenceConstants;

import jakarta.inject.Singleton;

/**
 * Provides configuration settings for code completion.
 * Reads from preference store and provides defaults.
 */
@Creatable
@Singleton
public class CompletionConfiguration {
    
    private IPreferenceStore preferenceStore;
    
    public CompletionConfiguration() {
        this.preferenceStore = Activator.getDefault().getPreferenceStore();
    }
    
    /**
     * Checks if LLM code completion is enabled.
     */
    public boolean isEnabled() {
        return preferenceStore.getBoolean(PreferenceConstants.ASSISTAI_COMPLETION_ENABLED);
    }
    
    /**
     * Gets the timeout duration for completion requests.
     */
    public Duration getTimeout() {
        int seconds = preferenceStore.getInt(PreferenceConstants.ASSISTAI_COMPLETION_TIMEOUT_SECONDS);
        if (seconds <= 0) {
            seconds = 8; // Default
        }
        return Duration.ofSeconds(seconds);
    }
    
    /**
     * Gets the hotkey string for triggering completion.
     */
    public String getHotkey() {
        String hotkey = preferenceStore.getString(PreferenceConstants.ASSISTAI_COMPLETION_HOTKEY);
        if (hotkey == null || hotkey.isBlank()) {
            hotkey = PreferenceConstants.ASSISTAI_COMPLETION_HOTKEY_DEFAULT;
        }
        return hotkey;
    }

    /**
     * When true, completion requests use Spring AI ({@link SpringAiStreamingCompletionClient})
     * instead of legacy {@code *StreamJavaHttpClient} implementations.
     */
    public boolean useSpringAi() {
        return preferenceStore.getBoolean( PreferenceConstants.ASSISTAI_COMPLETION_USE_SPRING_AI );
    }
    
}