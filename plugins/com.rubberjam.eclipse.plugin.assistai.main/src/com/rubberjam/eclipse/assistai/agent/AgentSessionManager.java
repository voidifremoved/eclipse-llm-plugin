package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IPersistentPreferenceStore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.springai.AgentChatEngine;
import com.rubberjam.eclipse.assistai.springai.AgentChatSession;
import com.rubberjam.eclipse.assistai.springai.AgentSendOptions;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptorRepository;
import com.rubberjam.eclipse.assistai.preferences.PreferenceConstants;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Creatable
@Singleton
public class AgentSessionManager
{
    @Inject
    private AgentChatEngine chatEngine;

    @Inject
    private ModelApiDescriptorRepository modelRepository;

    @Inject
    private AgentSystemPromptBuilder promptBuilder;

    private final Map<String, AgentSession> sessions = new LinkedHashMap<>();

    private final Map<String, String> tabTitles = new LinkedHashMap<>();

    private final Map<String, String> tabModelUids = new LinkedHashMap<>();

    private final Map<String, String> tabDraftTexts = new LinkedHashMap<>();

    private final Map<String, AgentInteractionMode> tabInteractionModes = new LinkedHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String activeTabId;

    private boolean loaded;

    public String createTab()
    {
        ensureLoaded();
        String tabId = UUID.randomUUID().toString();
        String modelUid = getDefaultModelUid();
        sessions.put( tabId, newSessionInternal( modelUid, Collections.emptyList() ) );
        tabTitles.put( tabId, "New Agent" );
        tabModelUids.put( tabId, modelUid );
        tabDraftTexts.put( tabId, "" );
        tabInteractionModes.put( tabId, AgentInteractionMode.AGENT );
        activeTabId = tabId;
        persistTabs();
        return tabId;
    }

    public AgentInteractionMode getTabInteractionMode( String tabId )
    {
        ensureLoaded();
        return tabInteractionModes.getOrDefault( tabId, AgentInteractionMode.AGENT );
    }

    public void setTabInteractionMode( String tabId, AgentInteractionMode mode )
    {
        ensureLoaded();
        if ( tabId == null || mode == null )
        {
            return;
        }
        tabInteractionModes.put( tabId, mode );
        persistTabs();
    }

    public List<String> getTabIds()
    {
        ensureLoaded();
        return new ArrayList<>( sessions.keySet() );
    }

    public String getActiveTabId()
    {
        ensureLoaded();
        return activeTabId;
    }

    public void setActiveTab( String tabId )
    {
        ensureLoaded();
        if ( sessions.containsKey( tabId ) && !tabId.equals( activeTabId ) )
        {
            activeTabId = tabId;
            persistTabs();
        }
    }

    public AgentSession getSession( String tabId )
    {
        ensureLoaded();
        return sessions.get( tabId );
    }

    public AgentSession getActiveSession()
    {
        ensureLoaded();
        if ( activeTabId == null || !sessions.containsKey( activeTabId ) )
        {
            createTab();
        }
        return sessions.get( activeTabId );
    }

    /** @deprecated use {@link #getActiveSession()} */
    public AgentSession getOrCreateSession()
    {
        return getActiveSession();
    }

    public void closeTab( String tabId )
    {
        ensureLoaded();
        sessions.remove( tabId );
        tabTitles.remove( tabId );
        tabModelUids.remove( tabId );
        tabDraftTexts.remove( tabId );
        tabInteractionModes.remove( tabId );
        if ( tabId.equals( activeTabId ) )
        {
            activeTabId = sessions.isEmpty() ? null : sessions.keySet().iterator().next();
        }
        persistTabs();
    }

    public boolean hasTabs()
    {
        ensureLoaded();
        return !sessions.isEmpty();
    }

    public String getTabTitle( String tabId )
    {
        ensureLoaded();
        return tabTitles.getOrDefault( tabId, "New Agent" );
    }

    public void setTabTitle( String tabId, String title )
    {
        ensureLoaded();
        if ( title != null && !title.isBlank() )
        {
            tabTitles.put( tabId, title );
            persistTabs();
        }
    }

    public AgentSession newSession()
    {
        ensureLoaded();
        if ( activeTabId == null )
        {
            createTab();
        }
        else
        {
            String modelUid = tabModelUids.getOrDefault( activeTabId, getDefaultModelUid() );
            sessions.put( activeTabId, newSessionInternal( modelUid, Collections.emptyList() ) );
            persistTabs();
        }
        return sessions.get( activeTabId );
    }

    public void switchModel( String modelUid )
    {
        ensureLoaded();
        if ( activeTabId != null )
        {
            switchModel( activeTabId, modelUid );
        }
    }

    public void switchModel( String tabId, String modelUid )
    {
        ensureLoaded();
        ModelApiDescriptor model = modelRepository.findById( modelUid )
            .orElseThrow( () -> new IllegalArgumentException( "Model not found: " + modelUid ) );

        AgentSession session = sessions.get( tabId );
        if ( session != null )
        {
            session.switchModel( model );
            tabModelUids.put( tabId, model.uid() );
            persistTabs();
        }
    }

    public ModelApiDescriptor getSelectedModel( String tabId )
    {
        ensureLoaded();
        String modelUid = tabModelUids.get( tabId );
        if ( modelUid != null )
        {
            return modelRepository.findById( modelUid ).orElse( modelRepository.getChatModelInUse() );
        }
        return modelRepository.getChatModelInUse();
    }

    public String getSelectedModelUid( String tabId )
    {
        ensureLoaded();
        String modelUid = tabModelUids.get( tabId );
        return modelUid != null ? modelUid : getDefaultModelUid();
    }

    public String getTabDraftText( String tabId )
    {
        ensureLoaded();
        return tabDraftTexts.getOrDefault( tabId, "" );
    }

    public void setTabDraftText( String tabId, String draftText )
    {
        ensureLoaded();
        if ( tabId == null || !sessions.containsKey( tabId ) )
        {
            return;
        }
        String normalized = draftText != null ? draftText : "";
        String current = tabDraftTexts.get( tabId );
        if ( normalized.equals( current ) )
        {
            return;
        }
        tabDraftTexts.put( tabId, normalized );
        persistTabs();
    }

    public void persistTabs()
    {
        List<AgentTabDescriptor> descriptors = new ArrayList<>();
        for ( String tabId : sessions.keySet() )
        {
            AgentSession session = sessions.get( tabId );
            List<AgentMessageSnapshot> messages = session != null
                    ? session.snapshotMessages()
                    : Collections.emptyList();
            AgentInteractionMode mode = tabInteractionModes.getOrDefault( tabId, AgentInteractionMode.AGENT );
            descriptors.add( new AgentTabDescriptor(
                    tabId,
                    tabTitles.getOrDefault( tabId, "New Agent" ),
                    tabModelUids.getOrDefault( tabId, getDefaultModelUid() ),
                    tabDraftTexts.getOrDefault( tabId, "" ),
                    tabId.equals( activeTabId ),
                    messages,
                    mode.name() ) );
        }
        try
        {
            getPreferenceStore().setValue(
                    PreferenceConstants.ASSISTAI_AGENT_TABS,
                    objectMapper.writeValueAsString( descriptors ) );
            savePreferenceStore();
        }
        catch ( JsonProcessingException e )
        {
            // Keep the current in-memory tabs if persistence fails.
        }
    }

    public void destroySession()
    {
        ensureLoaded();
        if ( activeTabId != null )
        {
            AgentSession session = sessions.get( activeTabId );
            if ( session != null )
            {
                session.clear();
            }
        }
        persistTabs();
    }

    /**
     * Refreshes workspace context and MCP tool list in the system prompt (call before each send).
     */
    public void refreshSystemPromptForSend( AgentSession session )
    {
        refreshSystemPromptForSend( session, AgentSendOptions.DEFAULT );
    }

    public void refreshSystemPromptForSend( AgentSession session, AgentSendOptions sendOptions )
    {
        if ( session == null )
        {
            return;
        }
        AgentSendOptions options = sendOptions != null ? sendOptions : AgentSendOptions.DEFAULT;
        String prompt = promptBuilder.buildSystemPrompt(
                options.getPromptFragmentFile(),
                options.getAdditionalSystemText() );
        session.updateSystemPrompt( prompt );
    }

    /**
     * Rebinds MCP tool callbacks on every open agent tab (e.g. after MCP preference changes).
     */
    public void refreshMcpToolsOnAllSessions()
    {
        ensureLoaded();
        String systemPrompt = promptBuilder.buildSystemPrompt();
        for ( AgentSession session : sessions.values() )
        {
            session.updateSystemPrompt( systemPrompt );
            String modelUid = session.getCurrentModelUid();
            if ( modelUid != null )
            {
                modelRepository.findById( modelUid ).ifPresent( session::switchModel );
            }
        }
    }

    private AgentSession newSessionInternal( String modelUid, List<AgentMessageSnapshot> messages )
    {
        String systemPrompt = promptBuilder.buildSystemPrompt();
        AgentChatSession chatSession = chatEngine.createSession( systemPrompt );
        AgentSession session = new AgentSession( chatSession );

        ModelApiDescriptor currentModel = modelUid != null
                ? modelRepository.findById( modelUid ).orElse( modelRepository.getChatModelInUse() )
                : modelRepository.getChatModelInUse();
        if ( currentModel != null )
        {
            session.initialize( currentModel );
        }
        session.restoreMessages( messages );
        return session;
    }

    private void ensureLoaded()
    {
        if ( loaded )
        {
            return;
        }
        loaded = true;
        boolean repaired = false;
        String json = getPreferenceStore().getString( PreferenceConstants.ASSISTAI_AGENT_TABS );
        if ( json == null || json.isBlank() )
        {
            createDefaultTabInternal();
            persistTabs();
            return;
        }
        try
        {
            List<AgentTabDescriptor> descriptors = objectMapper.readValue(
                    json,
                    new TypeReference<List<AgentTabDescriptor>>() {} );
            for ( AgentTabDescriptor descriptor : descriptors )
            {
                if ( descriptor == null || descriptor.tabId() == null || descriptor.tabId().isBlank() )
                {
                    continue;
                }
                sessions.put( descriptor.tabId(), newSessionInternal( descriptor.modelUid(), descriptor.messages() ) );
                tabTitles.put( descriptor.tabId(), descriptor.title() != null ? descriptor.title() : "New Agent" );
                tabModelUids.put( descriptor.tabId(), descriptor.modelUid() != null ? descriptor.modelUid() : getDefaultModelUid() );
                tabDraftTexts.put( descriptor.tabId(), descriptor.draftText() != null ? descriptor.draftText() : "" );
                tabInteractionModes.put(
                        descriptor.tabId(),
                        AgentInteractionMode.fromPersisted( descriptor.interactionMode() ) );
                if ( descriptor.active() )
                {
                    activeTabId = descriptor.tabId();
                }
            }
            if ( activeTabId == null && !sessions.isEmpty() )
            {
                activeTabId = sessions.keySet().iterator().next();
                repaired = true;
            }
        }
        catch ( Exception e )
        {
            sessions.clear();
            tabTitles.clear();
            tabModelUids.clear();
            tabDraftTexts.clear();
            activeTabId = null;
            repaired = true;
        }
        if ( sessions.isEmpty() )
        {
            createDefaultTabInternal();
            repaired = true;
        }
        if ( activeTabId == null || !sessions.containsKey( activeTabId ) )
        {
            activeTabId = sessions.keySet().iterator().next();
            repaired = true;
        }
        if ( repaired )
        {
            persistTabs();
        }
    }

    private void createDefaultTabInternal()
    {
        String tabId = UUID.randomUUID().toString();
        String modelUid = getDefaultModelUid();
        sessions.put( tabId, newSessionInternal( modelUid, Collections.emptyList() ) );
        tabTitles.put( tabId, "New Agent" );
        tabModelUids.put( tabId, modelUid );
        tabDraftTexts.put( tabId, "" );
        activeTabId = tabId;
    }

    private String getDefaultModelUid()
    {
        ModelApiDescriptor model = modelRepository.getChatModelInUse();
        return model != null ? model.uid() : null;
    }

    private IPreferenceStore getPreferenceStore()
    {
        return Activator.getDefault().getPreferenceStore();
    }

    private void savePreferenceStore()
    {
        IPreferenceStore store = getPreferenceStore();
        if ( store instanceof IPersistentPreferenceStore persistentStore )
        {
            try
            {
                persistentStore.save();
            }
            catch ( java.io.IOException e )
            {
                // Eclipse also persists preferences on shutdown; keep the in-memory tabs.
            }
        }
    }

}
