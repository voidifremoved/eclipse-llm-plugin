package com.rubberjam.eclipse.assistai.mcp.http;

import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jface.preference.IPreferenceStore;

import com.rubberjam.eclipse.assistai.Activator;

import jakarta.inject.Singleton;

@Creatable
@Singleton
public class HttpMcpServerPreferencesProvider
{
    public HttpMcpServerPreferences get()
    {
        IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();

        String hostname = preferenceStore.getString(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_HOSTNAME);
        int port = preferenceStore.getInt(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_PORT);
        String token = preferenceStore.getString(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN);

        return new HttpMcpServerPreferences(port, hostname, token);
    }

    public void save(HttpMcpServerPreferences preferences)
    {
        IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();

        preferenceStore.setValue(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_HOSTNAME, preferences.hostname());
        preferenceStore.setValue(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_PORT, preferences.port());
        preferenceStore.setValue(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN, preferences.token());
    }

    public boolean isEnabled()
    {
        IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
        return preferenceStore.getBoolean(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_ENABLED);
    }

    public void setEnabled(boolean enabled)
    {
        IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
        preferenceStore.setValue(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_ENABLED, enabled);
    }

    public void resetToDefaults()
    {
        IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();

        preferenceStore.setToDefault(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_HOSTNAME);
        preferenceStore.setToDefault(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_PORT);
        preferenceStore.setToDefault(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN);
        preferenceStore.setToDefault(com.rubberjam.eclipse.assistai.preferences.PreferenceConstants.ASSISTAI_MCP_HTTP_ENABLED);
    }

}
