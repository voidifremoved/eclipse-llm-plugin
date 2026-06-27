package com.rubberjam.eclipse.assistai.agent;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.resources.IProject;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchPage;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.chat.Attachment;
import com.rubberjam.eclipse.assistai.chat.Attachment.FileContentAttachment;
import static com.rubberjam.eclipse.assistai.tools.ImageUtilities.createPreview;
import com.rubberjam.eclipse.assistai.chat.ChatMessage;
import com.rubberjam.eclipse.assistai.prompt.ChatMessageFactory;
import com.rubberjam.eclipse.assistai.prompt.PromptContextValueProvider;
import com.rubberjam.eclipse.assistai.prompt.PromptRepository;
import com.rubberjam.eclipse.assistai.resources.CachedResource;
import com.rubberjam.eclipse.assistai.resources.IResourceCacheListener;
import com.rubberjam.eclipse.assistai.resources.ResourceCache;
import com.rubberjam.eclipse.assistai.resources.ResourceCacheEvent;
import com.rubberjam.eclipse.assistai.view.dnd.handlers.ResourceCacheHelper;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ide.IDE;
import com.rubberjam.eclipse.assistai.view.ChatView;
import com.rubberjam.eclipse.assistai.view.PartAccessor;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptorRepository;
import com.rubberjam.eclipse.assistai.springai.AgentSendOptions;
import com.rubberjam.eclipse.assistai.prompt.Prompts;
import com.rubberjam.eclipse.assistai.view.ApplyPatchWizardHelper;
import com.rubberjam.eclipse.assistai.view.ChatView.NotificationType;
import com.rubberjam.eclipse.assistai.mcp.services.CodeEditingService;
import com.rubberjam.eclipse.assistai.tools.ResourceUtilities;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import reactor.core.scheduler.Schedulers;

@Creatable
@Singleton
@SuppressWarnings("restriction")
public class AgentViewPresenter implements IResourceCacheListener
{
    @Inject private Provider<AgentSessionManager> sessionManagerProvider;
    @Inject private PartAccessor partAccessor;
    @Inject private PromptRepository promptRepository;
    @Inject private ChatMessageFactory chatMessageFactory;
    @Inject private PromptContextValueProvider promptContext;
    @Inject private ModelApiDescriptorRepository modelRepository;
    @Inject private ILog logger;
    @Inject private CodeEditingService codeEditingService;
    @Inject private ApplyPatchWizardHelper applyPatchWizardHelper;
    @Inject private UISynchronize uiSync;

    @Inject private AgentToolPolicy agentToolPolicy;

    @Inject private AgentCompilationErrorScope compilationErrorScope;

    @Inject private ResourceCache resourceCache;

    @Inject private ResourceCacheHelper resourceCacheHelper;

    private final Map<String, AgentTabState> tabStates = new HashMap<>();

    private ChatView registeredView;

    private boolean suppressTabSelection;

    private final IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
    private static final String LAST_SELECTED_DIR_KEY = "lastSelectedDirectory";

    public void onSendUserMessage(String text)
    {
        onSendUserMessage(text, null);
    }

    public void onSendUserMessage( String text, List<Attachment> attachments )
    {
        onSendUserMessage( text, attachments, null );
    }

    public void onInteractionModeSelected( AgentInteractionMode mode )
    {
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        String tabId = sessionManager.getActiveTabId();
        if ( tabId == null || mode == null )
        {
            return;
        }
        AgentTabState tabState = getTabState( tabId );
        tabState.interactionMode = mode;
        sessionManager.setTabInteractionMode( tabId, mode );
        if ( mode != AgentInteractionMode.PLAN )
        {
            tabState.awaitingPlanExecution = false;
        }
        syncModeToView( tabState );
    }

    public void onPlanModeToggled( boolean enabled )
    {
        onInteractionModeSelected( enabled ? AgentInteractionMode.PLAN : AgentInteractionMode.AGENT );
    }

    public void onExecutePlan()
    {
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        String tabId = sessionManager.getActiveTabId();
        if ( tabId == null )
        {
            return;
        }
        AgentTabState tabState = getTabState( tabId );
        if ( !tabState.awaitingPlanExecution || tabState.pendingPlanText == null
                || tabState.pendingPlanText.isBlank() )
        {
            return;
        }
        tabState.awaitingPlanExecution = false;
        tabState.interactionMode = AgentInteractionMode.AGENT;
        sessionManager.setTabInteractionMode( tabId, AgentInteractionMode.AGENT );
        syncModeToView( tabState );
        onSendUserMessage(
                "Execute the approved plan below using workspace MCP tools. Work through each checklist item.",
                null,
                AgentSendOptions.executePlan( tabState.pendingPlanText ) );
    }

    private void onSendUserMessage( String text, List<Attachment> attachments, AgentSendOptions sendOptions )
    {
        try
        {
            AgentSessionManager sessionManager = sessionManagerProvider.get();
            String tabId = sessionManager.getActiveTabId();
            if ( tabId == null )
            {
                tabId = sessionManager.createTab();
                ensureTabInView( tabId, sessionManager.getTabTitle( tabId ) );
            }

            ModelApiDescriptor model = sessionManager.getSelectedModel( tabId );
            if ( model == null )
            {
                applyToView( view -> view.showNotification(
                    "No AI model configured. Open Window > Preferences > Assist Agent > Models to add one.",
                    Duration.ofSeconds( 10 ),
                    NotificationType.WARNING ) );
                return;
            }

            AgentSession session = sessionManager.getSession( tabId );
            final String activeTabId = tabId;
            session.setToolCallEventListener( event -> onToolCallEvent( activeTabId, event ) );
            session.switchModel( model );

            AgentTabState tabState = getTabState( tabId );
            List<Attachment> tabAttachments = attachments != null
                    ? new ArrayList<>( attachments )
                    : new ArrayList<>( tabState.attachments );

            if ( text.startsWith( "/" ) )
            {
                String[] parts = text.split( " ", 2 );
                String command = parts[0].substring( 1 );
                String rest = parts.length > 1 ? parts[1] : "";
                String expanded = chatMessageFactory.expandSlashCommand( command, rest );
                if ( expanded != null )
                {
                    text = expanded;
                }
            }

            AgentSendOptions options = resolveSendOptions( tabState, sendOptions );
            sessionManager.refreshSystemPromptForSend( session, options );
            applyCompilationErrorScope( text );
            String userText = enrichWithActiveEditorContext( expandMentions( text ) );
            tabState.toolRoundCount = 0;
            String userMessageId = UUID.randomUUID().toString();
            updateTabTitle( tabId, userText );

            cacheMessage( tabState, userMessageId, "user", userText );
            String finalTabId = tabId;
            applyToView( view -> {
                tabState.draftText = "";
                sessionManager.setTabDraftText( finalTabId, "" );
                view.clearUserInput();
                view.appendMessage( userMessageId, "user" );
                view.setMessageHtml( userMessageId, userText );
                view.setInputEnabled( false );
            } );

            String assistantMessageId = UUID.randomUUID().toString();
            tabState.pendingAssistantMessageId = assistantMessageId;
            tabState.pendingAssistantHtml.setLength( 0 );
            cacheMessage( tabState, assistantMessageId, "assistant", "" );
            tabState.generating = true;
            tabState.stopRequested = false;
            tabState.generationId++;
            tabState.toolMessageIds.clear();
            tabState.toolNames.clear();
            tabState.pendingThinkingMessageId = null;
            tabState.pendingThinkingHtml.setLength( 0 );
            final int generationId = tabState.generationId;

            applyToView( view -> {
                view.appendMessage( assistantMessageId, "assistant" );
                view.setThinkingPlaceholder( assistantMessageId );
            } );

            final AgentSendOptions sendOptionsFinal = options;
            tabState.currentStream = session.sendMessage( userText, tabAttachments, userMessageId, options )
                .subscribeOn( Schedulers.boundedElastic() )
                .subscribe(
                    chunk -> {
                        if ( !isCurrentGeneration( tabState, generationId ) )
                        {
                            return;
                        }
                        if ( chunk.hasThinking() )
                        {
                            appendThinkingChunk( tabState, activeTabId, chunk.thinking() );
                        }
                        if ( chunk.hasText() )
                        {
                            tabState.pendingAssistantHtml.append( chunk.text() );
                            updateCachedMessageContent( tabState, assistantMessageId, tabState.pendingAssistantHtml.toString() );
                            if ( activeTabId.equals( sessionManager.getActiveTabId() ) )
                            {
                                String currentHtml = tabState.pendingAssistantHtml.toString();
                                applyToView( view -> view.setMessageHtml( assistantMessageId, currentHtml ) );
                            }
                        }
                    },
                    error -> {
                        if ( !isCurrentGeneration( tabState, generationId ) )
                        {
                            return;
                        }
                        logger.error( "Agent chat stream failed", error );
                        tabState.generating = false;
                        tabState.pendingAssistantMessageId = null;
                        String errorMessage = toUserFacingStreamError( error );
                        String finalMessage = tabState.pendingAssistantHtml.isEmpty()
                                ? errorMessage
                                : tabState.pendingAssistantHtml + "\n\n" + errorMessage;
                        updateCachedMessageContent( tabState, assistantMessageId, finalMessage );
                        session.appendAssistantResponse( assistantMessageId, finalMessage );
                        sessionManager.persistTabs();
                        compilationErrorScope.clear();
                        if ( activeTabId.equals( sessionManager.getActiveTabId() ) )
                        {
                            applyToView( view -> {
                                view.setMessageHtml( assistantMessageId, finalMessage );
                                view.setInputEnabled( true );
                            } );
                        }
                    },
                    () -> {
                        if ( !isCurrentGeneration( tabState, generationId ) )
                        {
                            return;
                        }
                        String responseText = tabState.pendingAssistantHtml.toString();
                        session.appendAssistantResponse( assistantMessageId, responseText );
                        updateCachedMessageContent( tabState, assistantMessageId, responseText );
                        if ( sendOptionsFinal == AgentSendOptions.PLAN_ONLY )
                        {
                            tabState.awaitingPlanExecution = true;
                            tabState.pendingPlanText = responseText;
                            tabState.taskItems.clear();
                            tabState.taskItems.addAll( AgentTaskChecklistParser.parse( responseText ) );
                        }
                        else
                        {
                            tabState.taskItems.clear();
                            tabState.taskItems.addAll( AgentTaskChecklistParser.parse( responseText ) );
                        }
                        tabState.generating = false;
                        tabState.pendingAssistantMessageId = null;
                        tabState.pendingThinkingMessageId = null;
                        tabState.pendingThinkingHtml.setLength( 0 );
                        sessionManager.persistTabs();
                        compilationErrorScope.clear();
                        if ( activeTabId.equals( sessionManager.getActiveTabId() ) )
                        {
                            applyToView( view -> {
                                view.setInputEnabled( true );
                                view.setExecutePlanEnabled( tabState.awaitingPlanExecution );
                                refreshTaskPanel( tabState );
                            } );
                        }
                    } );
            tabState.attachments.clear();
        }
        catch ( Exception e )
        {
            compilationErrorScope.clear();
            logger.error( "Failed to send message to AI model", e );
            applyToView( view -> {
                view.showNotification(
                    "Could not send message: " + e.getMessage(),
                    Duration.ofSeconds( 10 ),
                    NotificationType.ERROR );
                view.setInputEnabled( true );
            } );
        }
    }

    public void onStop()
    {
        String tabId = sessionManagerProvider.get().getActiveTabId();
        if ( tabId != null )
        {
            AgentTabState tabState = getTabState( tabId );
            tabState.stopRequested = true;
            tabState.generating = false;
            tabState.generationId++;
            disposeStream( tabState );
            AgentSession session = sessionManagerProvider.get().getSession( tabId );
            String partialResponse = tabState.pendingAssistantHtml.toString();
            String assistantMessageId = tabState.pendingAssistantMessageId;
            tabState.pendingAssistantMessageId = null;
            String stoppedResponse = partialResponse + "\n\n_Stopped._";
            if ( session != null && !partialResponse.isBlank() )
            {
                updateCachedMessageContent( tabState, assistantMessageId, stoppedResponse );
                session.appendAssistantResponse( assistantMessageId, stoppedResponse );
                sessionManagerProvider.get().persistTabs();
            }
            applyToView( view -> {
                if ( assistantMessageId != null )
                {
                    if ( partialResponse.isBlank() )
                    {
                        removeCachedMessage( tabState, assistantMessageId );
                        view.removeMessage( assistantMessageId );
                    }
                    else
                    {
                        view.setMessageHtml( assistantMessageId, stoppedResponse );
                    }
                }
                for ( Map.Entry<String, String> entry : tabState.toolMessageIds.entrySet() )
                {
                    updateCachedMessageContent(
                            tabState,
                            entry.getValue(),
                            toPersistedToolMessage(
                                    tabState.toolNames.getOrDefault( entry.getKey(), "Tool" ),
                                    "Stopped",
                                    "" ) );
                    if ( session != null )
                    {
                        session.updateMessageContent(
                                entry.getValue(),
                                toPersistedToolMessage(
                                        tabState.toolNames.getOrDefault( entry.getKey(), "Tool" ),
                                        "Stopped",
                                        "" ) );
                    }
                    view.updateToolCallMessage(
                            entry.getValue(),
                            tabState.toolNames.getOrDefault( entry.getKey(), "Tool" ),
                            "Stopped",
                            "" );
                }
            } );
            if ( session != null )
            {
                sessionManagerProvider.get().persistTabs();
            }
        }
        compilationErrorScope.clear();
        applyToView( view -> view.setInputEnabled( true ) );
    }

    public void onChatModelSelected(String modelId)
    {
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        String tabId = sessionManager.getActiveTabId();
        if ( tabId != null )
        {
            sessionManager.switchModel( tabId, modelId );
        }
        initializeAvailableModels();
    }

    public void onClear()
    {
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        String tabId = sessionManager.getActiveTabId();
        if ( tabId == null )
        {
            return;
        }
        AgentTabState tabState = getTabState( tabId );
        disposeStream( tabState );
        tabState.generating = false;
        tabState.pendingAssistantMessageId = null;
        tabState.pendingAssistantHtml.setLength( 0 );
        tabState.attachments.clear();
        tabState.displayMessages.clear();
        sessionManager.newSession();
        applyToView( view -> {
            tabState.draftText = "";
            sessionManager.setTabDraftText( tabId, "" );
            view.clearRenderedTabHistory( tabId );
            view.renderConversationHistory( tabId, getRenderableHistory( tabId, sessionManager.getSession( tabId ) ) );
            view.setUserInputText( tabState.draftText );
            view.setAttachments( tabState.attachments );
            view.setInputEnabled( true );
        } );
    }

    public void registerChatView( ChatView view )
    {
        registeredView = view;
        if ( resourceCache != null )
        {
            resourceCache.addCacheListener( this );
        }
        refreshContextPanel();
    }

    public void onChatViewCreated()
    {
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        if ( !sessionManager.hasTabs() )
        {
            String tabId = sessionManager.createTab();
            getTabState( tabId );
            applyToView( v -> {
                v.addAgentTab( tabId, sessionManager.getTabTitle( tabId ) );
                v.selectAgentTab( tabId );
                v.renderConversationHistory( tabId, getRenderableHistory( tabId, sessionManager.getSession( tabId ) ) );
                v.setUserInputText( getTabState( tabId ).draftText );
            } );
            return;
        }
        for ( String tabId : sessionManager.getTabIds() )
        {
            AgentTabState tabState = getTabState( tabId );
            tabState.draftText = sessionManager.getTabDraftText( tabId );
            applyToView( v -> v.addAgentTab( tabId, sessionManager.getTabTitle( tabId ) ) );
        }
        String activeId = sessionManager.getActiveTabId();
        if ( activeId != null )
        {
            onTabSelected( activeId );
        }
        initializeAvailableModels();
    }

    public void onNewAgentTab()
    {
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        String tabId = sessionManager.createTab();
        AgentTabState tabState = getTabState( tabId );
        tabState.interactionMode = AgentInteractionMode.AGENT;
        sessionManager.setTabInteractionMode( tabId, AgentInteractionMode.AGENT );
        tabState.attachments.clear();
        applyToView( v -> {
            v.addAgentTab( tabId, sessionManager.getTabTitle( tabId ) );
            suppressTabSelection = true;
            v.selectAgentTab( tabId );
            suppressTabSelection = false;
            v.renderConversationHistory( tabId, getRenderableHistory( tabId, sessionManager.getSession( tabId ) ) );
            v.setUserInputText( tabState.draftText );
            v.setAttachments( tabState.attachments );
            v.setInputEnabled( true );
            v.setInteractionMode( tabState.interactionMode );
        } );
        initializeAvailableModels();
    }

    public void onTabDraftChanged( String tabId, String draftText )
    {
        if ( tabId == null )
        {
            return;
        }
        String normalized = draftText != null ? draftText : "";
        getTabState( tabId ).draftText = normalized;
        sessionManagerProvider.get().setTabDraftText( tabId, normalized );
    }

    public void onCloseAgentTab( String tabId )
    {
        if ( tabId == null )
        {
            return;
        }
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        if ( !sessionManager.getTabIds().contains( tabId ) )
        {
            return;
        }
        disposeStream( tabStates.remove( tabId ) );
        sessionManager.closeTab( tabId );
        applyToView( v -> v.removeAgentTab( tabId ) );
        if ( !sessionManager.hasTabs() )
        {
            onNewAgentTab();
            return;
        }
        String activeId = sessionManager.getActiveTabId();
        if ( activeId != null )
        {
            onTabSelected( activeId );
        }
    }

    public void onTabSelected( String tabId )
    {
        if ( suppressTabSelection || tabId == null )
        {
            return;
        }
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        if ( sessionManager.getSession( tabId ) == null )
        {
            return;
        }
        sessionManager.setActiveTab( tabId );
        AgentTabState tabState = getTabState( tabId );
        tabState.interactionMode = sessionManager.getTabInteractionMode( tabId );
        tabState.draftText = sessionManager.getTabDraftText( tabId );
        AgentSession session = sessionManager.getSession( tabId );
        List<ChatMessage> renderHistory = getRenderableHistory( tabId, session );
        debugTab( "[AgentTabs] onTabSelected tabId=" + tabId
                + " activeTabId=" + sessionManager.getActiveTabId()
                + " cachedMessages=" + tabState.displayMessages.size()
                + " sessionMessages=" + ( session != null ? session.getHistory().size() : -1 )
                + " renderMessages=" + renderHistory.size() );
        applyToView( v -> {
            suppressTabSelection = true;
            v.selectAgentTab( tabId );
            suppressTabSelection = false;
            v.renderConversationHistory( tabId, renderHistory );
            if ( tabState.generating && tabState.pendingAssistantMessageId != null )
            {
                v.setMessageHtml( tabState.pendingAssistantMessageId, tabState.pendingAssistantHtml.toString() );
            }
            v.setUserInputText( tabState.draftText );
            v.setAttachments( new ArrayList<>( tabState.attachments ) );
            v.setInputEnabled( !tabState.generating );
            v.setInteractionMode( tabState.interactionMode );
            v.setExecutePlanEnabled( tabState.awaitingPlanExecution );
            refreshTaskPanel( tabState );
            refreshContextPanel();
        } );
        initializeAvailableModels();
    }

    @Override
    public void cacheChanged( ResourceCacheEvent event )
    {
        refreshContextPanel();
    }

    private void applyToView( java.util.function.Consumer<ChatView> action )
    {
        ChatView view = registeredView;
        if ( view != null )
        {
            uiSync.asyncExec( () -> {
                if ( !view.isDisposed() )
                {
                    action.accept( view );
                }
            } );
            return;
        }
        partAccessor.findMessageView().ifPresent( v -> uiSync.asyncExec( () -> {
            if ( !v.isDisposed() )
            {
                action.accept( v );
            }
        } ) );
    }

    private AgentTabState getTabState( String tabId )
    {
        return tabStates.computeIfAbsent( tabId, id -> new AgentTabState() );
    }

    private List<ChatMessage> getRenderableHistory( String tabId, AgentSession session )
    {
        AgentTabState tabState = getTabState( tabId );
        if ( !tabState.displayMessages.isEmpty() )
        {
            debugTab( "[AgentTabs] getRenderableHistory from cache tabId=" + tabId
                    + " count=" + tabState.displayMessages.size() );
            return copyMessages( tabState.displayMessages );
        }
        if ( session == null )
        {
            debugTab( "[AgentTabs] getRenderableHistory no-session tabId=" + tabId );
            return new ArrayList<>();
        }
        List<ChatMessage> history = session.getHistory();
        debugTab( "[AgentTabs] getRenderableHistory from session tabId=" + tabId
                + " sessionCount=" + history.size() );
        syncCachedMessages( tabState, history );
        return copyMessages( tabState.displayMessages );
    }

    private void debugTab( String message )
    {
        logger.info( message );
        System.out.println( message );
    }

    private void syncCachedMessages( AgentTabState tabState, List<ChatMessage> messages )
    {
        tabState.displayMessages.clear();
        if ( messages == null )
        {
            return;
        }
        for ( ChatMessage message : messages )
        {
            if ( message != null )
            {
                tabState.displayMessages.add( copyMessage( message ) );
            }
        }
    }

    private void cacheMessage( AgentTabState tabState, String messageId, String role, String content )
    {
        if ( tabState == null || messageId == null )
        {
            return;
        }
        ChatMessage message = new ChatMessage( messageId, role );
        message.setContent( content != null ? content : "" );
        tabState.displayMessages.add( message );
    }

    private void updateCachedMessageContent( AgentTabState tabState, String messageId, String content )
    {
        if ( tabState == null || messageId == null )
        {
            return;
        }
        for ( ChatMessage message : tabState.displayMessages )
        {
            if ( messageId.equals( message.getId() ) )
            {
                message.setContent( content != null ? content : "" );
                return;
            }
        }
    }

    private void removeCachedMessage( AgentTabState tabState, String messageId )
    {
        if ( tabState == null || messageId == null )
        {
            return;
        }
        for ( int i = tabState.displayMessages.size() - 1; i >= 0; i-- )
        {
            ChatMessage message = tabState.displayMessages.get( i );
            if ( message != null && messageId.equals( message.getId() ) )
            {
                tabState.displayMessages.remove( i );
            }
        }
    }

    private void moveCachedMessageToEnd( AgentTabState tabState, String messageId )
    {
        if ( tabState == null || messageId == null )
        {
            return;
        }
        ChatMessage matched = null;
        for ( int i = tabState.displayMessages.size() - 1; i >= 0; i-- )
        {
            ChatMessage message = tabState.displayMessages.get( i );
            if ( message != null && messageId.equals( message.getId() ) )
            {
                matched = message;
                tabState.displayMessages.remove( i );
                break;
            }
        }
        if ( matched != null )
        {
            tabState.displayMessages.add( matched );
        }
    }

    private List<ChatMessage> copyMessages( List<ChatMessage> messages )
    {
        List<ChatMessage> copy = new ArrayList<>();
        for ( ChatMessage message : messages )
        {
            if ( message != null )
            {
                copy.add( copyMessage( message ) );
            }
        }
        return copy;
    }

    private ChatMessage copyMessage( ChatMessage message )
    {
        ChatMessage copy = new ChatMessage( message.getId(), message.getName(), message.getRole() );
        copy.setContent( message.getContent() );
        copy.setAttachments( new ArrayList<>( message.getAttachments() ) );
        copy.setFunctionCall( message.getFunctionCall() );
        return copy;
    }

    private void ensureTabInView( String tabId, String title )
    {
        applyToView( v -> {
            if ( !v.hasAgentTab( tabId ) )
            {
                v.addAgentTab( tabId, title );
            }
        } );
    }

    private void updateTabTitle( String tabId, String userText )
    {
        String title = userText.strip();
        if ( title.length() > 28 )
        {
            title = title.substring( 0, 28 ) + "...";
        }
        sessionManagerProvider.get().setTabTitle( tabId, title );
        String finalTitle = title;
        applyToView( v -> v.setAgentTabTitle( tabId, finalTitle ) );
    }

    private void disposeStream( AgentTabState tabState )
    {
        if ( tabState == null || tabState.currentStream == null )
        {
            return;
        }
        try
        {
            Object stream = tabState.currentStream;
            java.lang.reflect.Method isDisposedMethod = stream.getClass().getMethod( "isDisposed" );
            boolean isDisposed = (Boolean) isDisposedMethod.invoke( stream );
            if ( !isDisposed )
            {
                java.lang.reflect.Method disposeMethod = stream.getClass().getMethod( "dispose" );
                disposeMethod.invoke( stream );
            }
        }
        catch ( Exception e )
        {
            // Ignore
        }
        tabState.currentStream = null;
    }

    private boolean isCurrentGeneration( AgentTabState tabState, int generationId )
    {
        return tabState != null && !tabState.stopRequested && tabState.generationId == generationId;
    }

    private void onToolCallEvent( String tabId, ToolCallEvent event )
    {
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        AgentTabState tabState = getTabState( tabId );
        if ( tabState.stopRequested || !tabState.generating )
        {
            return;
        }
        if ( event.status() == ToolCallStatus.STARTED )
        {
            if ( isThinkingTool( event.toolName() ) )
            {
                String thought = extractThoughtFromToolInput( event.input() );
                if ( thought != null && !thought.isBlank() )
                {
                    appendThinkingChunk( tabState, tabId, thought );
                }
                updateAgentActivity( tabState, tabId, "Thinking..." );
                return;
            }
            tabState.toolStartTimes.put( event.id(), Long.valueOf( System.currentTimeMillis() ) );
            tabState.toolRoundCount++;
            int maxRounds = agentToolPolicy.getMaxToolRounds();
            if ( tabState.toolRoundCount > maxRounds )
            {
                applyToView( view -> view.showNotification(
                        "Tool round limit reached (" + maxRounds + "). Stop and send a new message to continue.",
                        Duration.ofSeconds( 12 ),
                        NotificationType.WARNING ) );
                onStop();
                return;
            }
            updateAgentActivity(
                    tabState,
                    tabId,
                    "Round " + tabState.toolRoundCount + "/" + maxRounds + ": " + event.toolName() + "..." );
            AgentTaskChecklistParser.markCompletedByTool( tabState.taskItems, event.toolName() );
            refreshTaskPanel( tabState );
            String messageId = UUID.randomUUID().toString();
            tabState.toolMessageIds.put( event.id(), messageId );
            tabState.toolNames.put( event.id(), event.toolName() );
            cacheMessage( tabState, messageId, "tool", toPersistedToolMessage( event.toolName(), "Running", event.input() ) );
            if ( tabState.pendingAssistantMessageId != null )
            {
                moveCachedMessageToEnd( tabState, tabState.pendingAssistantMessageId );
            }
            AgentSession session = sessionManager.getSession( tabId );
            if ( session != null )
            {
                session.appendToolMessage(
                        messageId,
                        event.toolName(),
                        toPersistedToolMessage( event.toolName(), "Running", event.input() ) );
                sessionManager.persistTabs();
            }
            if ( tabId.equals( sessionManager.getActiveTabId() ) )
            {
                String input = AgentToolCallFormatter.truncateDetails( event.input() );
                applyToView( view -> {
                    view.appendToolCallMessage(
                            messageId,
                            event.toolName(),
                            "Running",
                            input );
                    if ( tabState.pendingAssistantMessageId != null )
                    {
                        view.moveMessageToEnd( tabState.pendingAssistantMessageId );
                    }
                } );
            }
            return;
        }

        if ( isThinkingTool( event.toolName() ) )
        {
            if ( event.status() == ToolCallStatus.FINISHED || event.status() == ToolCallStatus.FAILED )
            {
                String thought = resolveThoughtText( event.input(), event.output() );
                if ( thought != null && !thought.isBlank() )
                {
                    setThinkingContent( tabState, tabId, thought );
                }
            }
            return;
        }
        String messageId = tabState.toolMessageIds.get( event.id() );
        if ( messageId == null )
        {
            return;
        }
        String status = event.status() == ToolCallStatus.FINISHED ? "Finished" : "Failed";
        Long started = tabState.toolStartTimes.remove( event.id() );
        String duration = started != null
                ? AgentToolCallFormatter.formatDuration( started.longValue(), System.currentTimeMillis() )
                : "";
        if ( !duration.isBlank() )
        {
            status = status + " (" + duration + ")";
        }
        String localStatus = status;
        String details = AgentToolCallFormatter.truncateDetails(
                event.output() != null ? event.output() : "" );
        updateCachedMessageContent( tabState, messageId, toPersistedToolMessage( event.toolName(), status, details ) );
        AgentSession session = sessionManager.getSession( tabId );
        if ( session != null )
        {
            session.updateMessageContent(
                    messageId,
                    toPersistedToolMessage( event.toolName(), status, details ) );
            sessionManager.persistTabs();
        }
        if ( tabId.equals( sessionManager.getActiveTabId() ) )
        {
            List<String> paths = AgentToolCallFormatter.extractOpenablePaths( details );
            applyToView( view -> view.updateToolCallMessage(
                    messageId,
                    event.toolName(),
                    localStatus,
                    details,
                    paths ) );
        }
    }

    public void focusAgentView()
    {
        partAccessor.findMessageView().ifPresent( ChatView::setFocus );
    }

    private String toPersistedToolMessage( String toolName, String status, String details )
    {
        return "**Tool:** `" + ( toolName != null ? toolName : "Tool" ) + "`\n\n"
                + "**Status:** " + ( status != null ? status : "" ) + "\n\n"
                + "```json\n" + ( details != null ? details : "" ) + "\n```";
    }

    /**
     * Appends active editor path when the user refers to "this file" so the model does not ask for a path.
     */
    private String enrichWithActiveEditorContext( String userText )
    {
        if ( userText == null || userText.isBlank() || !refersToCurrentEditor( userText ) )
        {
            return userText;
        }
        String project = promptContext.getContextValue( "currentProjectName" );
        String path = promptContext.getContextValue( "currentFilePath" );
        if ( path == null || path.isBlank() )
        {
            return userText;
        }
        StringBuilder note = new StringBuilder();
        note.append( "\n\n[Active editor context: " );
        if ( project != null && !project.isBlank() )
        {
            note.append( "project=" ).append( project ).append( ", " );
        }
        note.append( "file=" ).append( path ).append( ']' );
        if ( refersToFixRequest( userText ) || refersToCurrentEditor( userText ) )
        {
            note.append( "\n[Task: Fix ONLY this file. Call eclipse-ide__getCompilationErrors with projectName and "
                    + "filePath set to the path above. Do not fix errors in other files or run Maven/build unless "
                    + "the user explicitly asked. Apply executeQuickFix and/or applyPatch, then re-check with the "
                    + "same filePath. Do not only list errors.]" );
        }
        return userText + note.toString();
    }

    private void applyCompilationErrorScope( String userText )
    {
        if ( refersToProjectWideFix( userText ) )
        {
            compilationErrorScope.clear();
            return;
        }
        if ( !refersToCurrentEditor( userText ) && !refersToFixRequest( userText ) )
        {
            compilationErrorScope.clear();
            return;
        }
        String project = promptContext.getContextValue( "currentProjectName" );
        String path = promptContext.getContextValue( "currentFilePath" );
        if ( path == null || path.isBlank() )
        {
            compilationErrorScope.clear();
            return;
        }
        compilationErrorScope.set( new AgentCompilationErrorScope.Scope( project, path ) );
    }

    private void applyCompilationErrorScopeForCurrentEditor()
    {
        String project = promptContext.getContextValue( "currentProjectName" );
        String path = promptContext.getContextValue( "currentFilePath" );
        if ( path == null || path.isBlank() )
        {
            compilationErrorScope.clear();
            return;
        }
        compilationErrorScope.set( new AgentCompilationErrorScope.Scope( project, path ) );
    }

    private AgentSendOptions resolveSendOptions( AgentTabState tabState, AgentSendOptions explicit )
    {
        if ( explicit != null )
        {
            return explicit;
        }
        if ( tabState.interactionMode == AgentInteractionMode.PLAN && !tabState.awaitingPlanExecution )
        {
            return AgentSendOptions.PLAN_ONLY;
        }
        if ( tabState.interactionMode == AgentInteractionMode.ASK )
        {
            return AgentSendOptions.askMode( agentToolPolicy.resolveAskModeAllowedToolNames() );
        }
        return AgentSendOptions.DEFAULT;
    }

    private void syncModeToView( AgentTabState tabState )
    {
        applyToView( view -> {
            view.setInteractionMode( tabState.interactionMode );
            view.setExecutePlanEnabled( tabState.awaitingPlanExecution );
        } );
    }

    public void refreshContextPanel()
    {
        AgentContextSnapshot snapshot = buildContextSnapshot();
        applyToView( view -> view.updateContextPanel( snapshot ) );
    }

    private AgentContextSnapshot buildContextSnapshot()
    {
        String project = promptContext.getContextValue( "currentProjectName" );
        String path = promptContext.getContextValue( "currentFilePath" );
        String name = promptContext.getContextValue( "currentFileName" );
        String selection = promptContext.getContextValue( "selectedContent" );
        List<String> labels = new ArrayList<>();
        if ( resourceCache != null )
        {
            for ( CachedResource cached : resourceCache.getAll().values() )
            {
                labels.add( cached.descriptor().uri().toString() );
            }
        }
        return new AgentContextSnapshot( project, path, name, selection, labels );
    }

    public void onAddEditorSelectionToCache()
    {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow() != null
                ? PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                : null;
        if ( page == null || page.getSelection() == null )
        {
            return;
        }
        if ( page.getSelection() instanceof IStructuredSelection structured )
        {
            Object element = structured.getFirstElement();
            if ( element instanceof IFile file )
            {
                resourceCacheHelper.addWorkspaceFile( file );
            }
        }
        refreshContextPanel();
    }

    public void onOpenWorkspacePath( String workspacePath )
    {
        if ( workspacePath == null || workspacePath.isBlank() )
        {
            return;
        }
        uiSync.asyncExec( () -> {
            try
            {
                IPath path = org.eclipse.core.runtime.Path.fromOSString( workspacePath );
                IFile file = org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot().getFile( path );
                if ( file.exists() )
                {
                    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                    IDE.openEditor( page, file );
                }
            }
            catch ( Exception e )
            {
                logger.error( "Failed to open path from tool result", e );
            }
        } );
    }

    private String expandMentions( String text )
    {
        if ( text == null || text.isBlank() )
        {
            return text;
        }
        String result = text;
        if ( result.contains( "@File" ) )
        {
            String path = promptContext.getContextValue( "currentFilePath" );
            String project = promptContext.getContextValue( "currentProjectName" );
            result = result.replace( "@File", "[@File " + project + "/" + path + "]" );
        }
        if ( result.contains( "@Project" ) )
        {
            result = result.replace( "@Project", "[@Project " + promptContext.getContextValue( "currentProjectName" ) + "]" );
        }
        if ( result.contains( "@Selection" ) )
        {
            String sel = promptContext.getContextValue( "selectedContent" );
            if ( sel == null || sel.isBlank() )
            {
                result = result.replace( "@Selection", "[@Selection (empty)]" );
            }
            else
            {
                result = result.replace( "@Selection", "[@Selection]\n" + sel );
            }
        }
        return result;
    }

    private void refreshTaskPanel( AgentTabState tabState )
    {
        if ( registeredView == null )
        {
            return;
        }
        applyToView( view -> view.setTaskChecklist( tabState.taskItems ) );
    }

    private void updateAgentActivity( AgentTabState tabState, String tabId, String status )
    {
        if ( tabState.pendingAssistantMessageId == null || status == null || status.isBlank() )
        {
            return;
        }
        if ( tabId.equals( sessionManagerProvider.get().getActiveTabId() ) )
        {
            String assistantId = tabState.pendingAssistantMessageId;
            applyToView( view -> view.setAgentActivityStatus( assistantId, status ) );
        }
    }

    private void setThinkingContent( AgentTabState tabState, String tabId, String thought )
    {
        if ( thought == null || thought.isBlank() )
        {
            return;
        }
        boolean isNew = tabState.pendingThinkingMessageId == null;
        if ( isNew )
        {
            tabState.pendingThinkingMessageId = UUID.randomUUID().toString();
        }
        tabState.pendingThinkingHtml.setLength( 0 );
        tabState.pendingThinkingHtml.append( thought );
        String thinkingId = tabState.pendingThinkingMessageId;
        String combined = tabState.pendingThinkingHtml.toString();
        if ( isNew )
        {
            cacheMessage( tabState, thinkingId, "thinking", combined );
        }
        else
        {
            updateCachedMessageContent( tabState, thinkingId, combined );
        }
        if ( tabId.equals( sessionManagerProvider.get().getActiveTabId() ) )
        {
            if ( isNew )
            {
                applyToView( view -> {
                    view.appendThinkingMessage( thinkingId, combined );
                    if ( tabState.pendingAssistantMessageId != null )
                    {
                        view.moveMessageToEnd( tabState.pendingAssistantMessageId );
                    }
                } );
            }
            else
            {
                applyToView( view -> view.updateThinkingMessage( thinkingId, combined ) );
            }
        }
    }

    private void appendThinkingChunk( AgentTabState tabState, String tabId, String thought )
    {
        if ( thought == null || thought.isBlank() )
        {
            return;
        }
        if ( tabState.pendingThinkingMessageId == null )
        {
            setThinkingContent( tabState, tabId, thought );
            return;
        }
        if ( tabState.pendingThinkingHtml.length() > 0 )
        {
            tabState.pendingThinkingHtml.append( '\n' );
        }
        tabState.pendingThinkingHtml.append( thought );
        String combined = tabState.pendingThinkingHtml.toString();
        String thinkingId = tabState.pendingThinkingMessageId;
        updateCachedMessageContent( tabState, thinkingId, combined );
        if ( tabId.equals( sessionManagerProvider.get().getActiveTabId() ) )
        {
            applyToView( view -> view.updateThinkingMessage( thinkingId, combined ) );
        }
    }

    private static String resolveThoughtText( String input, String output )
    {
        String thought = extractThoughtFromToolInput( input );
        if ( ( thought == null || thought.isBlank() ) && output != null && !output.isBlank() )
        {
            return output;
        }
        if ( output != null && !output.isBlank() && thought != null && !thought.isBlank()
                && !output.equals( thought ) )
        {
            return thought + "\n\n" + output;
        }
        return thought;
    }

    private static boolean isThinkingTool( String toolName )
    {
        return toolName != null && toolName.endsWith( "__think" );
    }

    private static String extractThoughtFromToolInput( String input )
    {
        if ( input == null || input.isBlank() )
        {
            return "";
        }
        String trimmed = input.trim();
        if ( trimmed.startsWith( "{" ) )
        {
            int thoughtKey = trimmed.indexOf( "\"thought\"" );
            if ( thoughtKey < 0 )
            {
                thoughtKey = trimmed.indexOf( "'thought'" );
            }
            if ( thoughtKey >= 0 )
            {
                int colon = trimmed.indexOf( ':', thoughtKey );
                if ( colon >= 0 )
                {
                    int valueStart = colon + 1;
                    while ( valueStart < trimmed.length()
                            && Character.isWhitespace( trimmed.charAt( valueStart ) ) )
                    {
                        valueStart++;
                    }
                    if ( valueStart < trimmed.length() && trimmed.charAt( valueStart ) == '"' )
                    {
                        int end = valueStart + 1;
                        StringBuilder value = new StringBuilder();
                        while ( end < trimmed.length() )
                        {
                            char c = trimmed.charAt( end );
                            if ( c == '\\' && end + 1 < trimmed.length() )
                            {
                                value.append( trimmed.charAt( end + 1 ) );
                                end += 2;
                                continue;
                            }
                            if ( c == '"' )
                            {
                                return value.toString();
                            }
                            value.append( c );
                            end++;
                        }
                    }
                }
            }
        }
        return trimmed;
    }

    private static boolean refersToFixRequest( String text )
    {
        String lower = text.toLowerCase();
        return lower.contains( "fix error" )
                || lower.contains( "fix compilation" )
                || lower.contains( "resolve error" )
                || lower.contains( "correct error" );
    }

    private static boolean refersToProjectWideFix( String text )
    {
        String lower = text.toLowerCase();
        return lower.contains( "whole project" )
                || lower.contains( "entire project" )
                || lower.contains( "all files" )
                || lower.contains( "across the project" )
                || lower.contains( "workspace-wide" );
    }

    private static boolean refersToCurrentEditor( String text )
    {
        String lower = text.toLowerCase();
        return lower.contains( "this file" )
                || lower.contains( "current file" )
                || lower.contains( "the file" )
                || lower.contains( "this class" )
                || lower.contains( "errors here" )
                || lower.contains( "fix errors" );
    }

    private String toUserFacingStreamError( Throwable error )
    {
        String message = error != null && error.getMessage() != null ? error.getMessage() : "";
        if ( message.contains( "No ToolCallback found for tool name:" ) )
        {
            String toolName = extractMissingToolName( message );
            if ( looksLikeJsonObject( toolName ) )
            {
                return "The model emitted a malformed tool call: it put JSON arguments in the tool-name field instead of using a registered tool name. "
                        + "No workspace change was made. Try again with a model that supports OpenAI-compatible tool calling, or switch to ASK/PLAN mode for analysis-only requests. "
                        + "Details: " + message;
            }
            return "A workspace tool was invoked but is not registered with Spring AI ("
                    + message
                    + "). Rebuild and restart Eclipse with the latest AssistAI plugin, then check "
                    + "Window > Preferences > Assist Agent > MCP Servers that eclipse-ide is enabled and RUNNING. "
                    + "Retry your request with the target file open in the editor.";
        }
        if ( isBadRequest( error ) )
        {
            return "The model API rejected the request (HTTP 400). This often means invalid chat history, "
                    + "unsupported tools, or a model/API mismatch. Try clearing the agent tab and sending again. "
                    + "Details: " + message;
        }
        return "Error: " + message;
    }

    private static String extractMissingToolName( String message )
    {
        String marker = "No ToolCallback found for tool name:";
        int index = message.indexOf( marker );
        if ( index < 0 )
        {
            return "";
        }
        return message.substring( index + marker.length() ).trim();
    }

    private static boolean looksLikeJsonObject( String value )
    {
        if ( value == null )
        {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith( "{" ) && trimmed.endsWith( "}" );
    }

    private static boolean isBadRequest( Throwable error )
    {
        Throwable current = error;
        while ( current != null )
        {
            if ( "BadRequestException".equals( current.getClass().getSimpleName() ) )
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public void onSendPredefinedPrompt( com.rubberjam.eclipse.assistai.prompt.Prompts type, ChatMessage message )
    {
        if ( type == com.rubberjam.eclipse.assistai.prompt.Prompts.FIX_ERRORS )
        {
            applyCompilationErrorScopeForCurrentEditor();
        }
        onSendUserMessage( message.getContent(), message.getAttachments() );
    }

    @PostConstruct
    public void init()
    {
        refreshViewState();
    }

    public void onViewVisible()
    {
        refreshViewState();
        refreshContextPanel();
    }

    private void refreshViewState()
    {
        initializeAvailableModels();
        updateAutocomplete();
    }

    private void initializeAvailableModels()
    {
        AgentSessionManager sessionManager = sessionManagerProvider.get();
        String tabId = sessionManager.getActiveTabId();
        ModelApiDescriptor selectedModel = tabId != null
                ? sessionManager.getSelectedModel( tabId )
                : modelRepository.getChatModelInUse();
        List<ModelApiDescriptor> models = modelRepository.listModelApiDescriptors();
        String selectedId = selectedModel != null ? selectedModel.uid() : "";
        boolean toolsSupported = selectedModel == null || selectedModel.functionCalling();
        String capabilityWarning = toolsSupported
                ? null
                : "Tools are disabled for this model. Use Ask mode for Q&A or select a model with function calling.";
        applyToView( view -> {
            view.setAvailableModels( models, selectedId );
            view.setCapabilityWarning( capabilityWarning );
        } );
    }

    private void updateAutocomplete()
    {
        Map<String, String> mappings = promptRepository.getAllPrompts()
            .stream()
            .collect( Collectors.toMap( Prompts::getCommandName, Prompts::getDescription ) );
        applyToView( view -> view.setAutocompleteModel( mappings ) );
    }

    public void onRemoveAttachment( int index )
    {
        String tabId = sessionManagerProvider.get().getActiveTabId();
        if ( tabId == null )
        {
            return;
        }
        AgentTabState tabState = getTabState( tabId );
        if ( index >= 0 && index < tabState.attachments.size() )
        {
            tabState.attachments.remove( index );
            applyToView( view -> view.setAttachments( tabState.attachments ) );
        }
    }

    public void onAddAttachment() {
        Display display = PlatformUI.getWorkbench().getDisplay();
        if (Objects.isNull(display)) {
            logger.error("No active display");
            return;
        }

        uiSync.asyncExec(() -> {
            if ( display.isDisposed() )
            {
                return;
            }
            var shell = display.getActiveShell();
            if ( shell == null || shell.isDisposed() )
            {
                return;
            }
            FileDialog fileDialog = new FileDialog(shell, SWT.OPEN);
            fileDialog.setText("Select an Image");

            // Retrieve the last selected directory from the preferences
            String lastSelectedDirectory = preferences.getString(LAST_SELECTED_DIR_KEY);
            fileDialog.setFilterPath(lastSelectedDirectory);

            fileDialog.setFilterExtensions(new String[] { "*.png", "*.jpeg", "*.jpg" });
            fileDialog.setFilterNames(new String[] { "PNG files (*.png)", "JPEG files (*.jpeg, *.jpg)" });

            String selectedFilePath = fileDialog.open();

            if (selectedFilePath != null) {
                // Save the last selected directory back to the preferences
                String newLastSelectedDirectory = new File(selectedFilePath).getParent();
                preferences.putValue(LAST_SELECTED_DIR_KEY, newLastSelectedDirectory);

                ImageData[] imageDataArray = new ImageLoader().load(selectedFilePath);
                if ( imageDataArray.length > 0 )
                {
                    String tabId = sessionManagerProvider.get().getActiveTabId();
                    if ( tabId == null )
                    {
                        tabId = sessionManagerProvider.get().createTab();
                        ensureTabInView( tabId, "New Agent" );
                    }
                    AgentTabState tabState = getTabState( tabId );
                    tabState.attachments.add( new Attachment.ImageAttachment(
                            imageDataArray[0], createPreview( imageDataArray[0] ) ) );
                    applyToView( messageView -> messageView.setAttachments( tabState.attachments ) );
                }
            }
        });
    }

    public void onReplayLastMessage() {
        logger.info("Replaying last message with current model");
        AgentSession session = sessionManagerProvider.get().getActiveSession();
        List<ChatMessage> history = session.getHistory();
        if (history.isEmpty()) {
            return;
        }

        if ("assistant".equals(history.get(history.size() - 1).getRole())) {
            ChatMessage lastMessage = history.get(history.size() - 1);
            session.removeLastMessage();
            applyToView(view -> {
                view.removeMessage(lastMessage.getId());
            });
        }

        // We need to re-send the last user message, but without adding it to history again
        // Actually, the simplest way is to remove the last user message as well and re-send it through onSendUserMessage
        List<ChatMessage> updatedHistory = session.getHistory();
        if (!updatedHistory.isEmpty() && "user".equals(updatedHistory.get(updatedHistory.size() - 1).getRole())) {
            ChatMessage lastUserMessage = updatedHistory.get(updatedHistory.size() - 1);
            session.removeLastMessage();
            applyToView(view -> {
                view.removeMessage(lastUserMessage.getId());
            });
            onSendUserMessage(lastUserMessage.getContent(), lastUserMessage.getAttachments());
        }
    }

    public void onAttachmentAdded( ImageData imageData )
    {
        String tabId = sessionManagerProvider.get().getActiveTabId();
        if ( tabId == null )
        {
            tabId = sessionManagerProvider.get().createTab();
            ensureTabInView( tabId, "New Agent" );
        }
        AgentTabState tabState = getTabState( tabId );
        tabState.attachments.add( new Attachment.ImageAttachment( imageData, createPreview( imageData ) ) );
        applyToView( messageView -> messageView.setAttachments( tabState.attachments ) );
    }

    public void onAttachmentAdded( FileContentAttachment attachment )
    {
        String tabId = sessionManagerProvider.get().getActiveTabId();
        if ( tabId == null )
        {
            tabId = sessionManagerProvider.get().createTab();
            ensureTabInView( tabId, "New Agent" );
        }
        AgentTabState tabState = getTabState( tabId );
        tabState.attachments.add( attachment );
        applyToView( messageView -> messageView.setAttachments( tabState.attachments ) );
    }

    public void onCopyCode(String codeBlock) {
        // We use the clipboard to copy code block
        Display display = Display.getDefault();
        if ( display == null || display.isDisposed() )
        {
            return;
        }
        display.asyncExec(() -> {
            org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
            org.eclipse.swt.dnd.TextTransfer textTransfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
            clipboard.setContents(new Object[] { codeBlock }, new org.eclipse.swt.dnd.Transfer[] { textTransfer });
            clipboard.dispose();
        });
    }

    public void onApplyPatch(String codeBlock) {
        Display display = Display.getDefault();
        if ( display == null || display.isDisposed() )
        {
            return;
        }
        display.asyncExec(() -> {
            Optional.ofNullable(PlatformUI.getWorkbench())
                    .map(workbench -> workbench.getActiveWorkbenchWindow())
                    .map(window -> window.getActivePage())
                    .map(page -> page.getActiveEditor())
                    .flatMap(editor -> Optional.ofNullable(editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class)))
                    .ifPresent(textEditor -> {
                        if (textEditor.getEditorInput() instanceof org.eclipse.ui.part.FileEditorInput) {
                            org.eclipse.ui.part.FileEditorInput fileInput = (org.eclipse.ui.part.FileEditorInput) textEditor.getEditorInput();
                            String projectName = fileInput.getFile().getProject().getName();
                            applyPatchWizardHelper.showApplyPatchWizardDialog(codeBlock, projectName);
                        }
                    });
        });
    }

    public void onInsertCode(String codeBlock) {
        Display display = Display.getDefault();
        if ( display == null || display.isDisposed() )
        {
            return;
        }
        display.asyncExec(() -> {
            Optional.ofNullable(PlatformUI.getWorkbench())
                    .map(workbench -> workbench.getActiveWorkbenchWindow())
                    .map(window -> window.getActivePage())
                    .map(page -> page.getActiveEditor())
                    .flatMap(editor -> Optional.ofNullable(editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class)))
                    .ifPresent(textEditor -> {
                        var selectionProvider = textEditor.getSelectionProvider();
                        var document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
                        if (selectionProvider != null && document != null) {
                            var selection = (org.eclipse.jface.text.ITextSelection) selectionProvider.getSelection();
                            try {
                                if (selection.getLength() > 0) {
                                    document.replace(selection.getOffset(), selection.getLength(), codeBlock);
                                } else {
                                    document.replace(selection.getOffset(), 0, codeBlock);
                                }
                            } catch (org.eclipse.jface.text.BadLocationException e) {
                                logger.error("Error inserting code at location", e);
                            }
                        }
                    });
        });
    }

    public void onDiffCode(String codeBlock) {
        Display display = Display.getDefault();
        if ( display == null || display.isDisposed() )
        {
            return;
        }
        display.asyncExec(() -> {
            Optional.ofNullable(PlatformUI.getWorkbench())
                    .map(workbench -> workbench.getActiveWorkbenchWindow())
                    .map(window -> window.getActivePage())
                    .map(page -> page.getActiveEditor())
                    .flatMap(editor -> Optional.ofNullable(editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class)))
                    .ifPresent(textEditor -> {
                        if (textEditor.getEditorInput() instanceof org.eclipse.ui.part.FileEditorInput) {
                            org.eclipse.ui.part.FileEditorInput fileInput = (org.eclipse.ui.part.FileEditorInput) textEditor.getEditorInput();
                            String projectName = fileInput.getFile().getProject().getName();
                            String filePath = fileInput.getFile().getProjectRelativePath().toString();
                            String diff = codeEditingService.generateCodeDiff(projectName, filePath, codeBlock, 3);
                            if (diff != null && !diff.isBlank()) {
                                applyPatchWizardHelper.showApplyPatchWizardDialog(diff, projectName);
                            }
                        }
                    });
        });
    }

    public void onNewFile(String codeBlock, String lang) {
        Display display = Display.getDefault();
        if ( display == null || display.isDisposed() )
        {
            return;
        }
        display.asyncExec(() -> {
            IProject project = Optional.ofNullable(PlatformUI.getWorkbench())
                    .map(IWorkbench::getActiveWorkbenchWindow)
                    .map(IWorkbenchWindow::getActivePage)
                    .map(IWorkbenchPage::getActiveEditor)
                    .map(editor -> editor.getEditorInput())
                    .filter(input -> input instanceof org.eclipse.ui.part.FileEditorInput)
                    .map(input -> ((org.eclipse.ui.part.FileEditorInput) input).getFile().getProject())
                    .orElse(null);
            if (project != null) {
                String suggestedFileName = ResourceUtilities.getSuggestedFileName(lang, codeBlock);
                IPath suggestedPath = ResourceUtilities.getSuggestedPath(project, lang, codeBlock);
                org.eclipse.ui.dialogs.WizardNewFileCreationPage newFilePage = new org.eclipse.ui.dialogs.WizardNewFileCreationPage("NewFilePage", new org.eclipse.jface.viewers.StructuredSelection(project));
                newFilePage.setTitle("New File");
                newFilePage.setDescription(String.format("Create a new %s file in the project", ResourceUtilities.getFileExtensionForLang(lang)));
                if (suggestedPath != null) {
                    newFilePage.setContainerFullPath(suggestedPath);
                }
                newFilePage.setFileName(suggestedFileName);
                org.eclipse.jface.wizard.Wizard wizard = new org.eclipse.jface.wizard.Wizard() {
                    @Override
                    public boolean performFinish() {
                        return true;
                    }
                };
                wizard.addPage(newFilePage);
                org.eclipse.jface.wizard.WizardDialog dialog = new org.eclipse.jface.wizard.WizardDialog(Display.getDefault().getActiveShell(), wizard);
                dialog.open();
            }
        });
    }

    public void onRemoveMessage( String messageId )
    {
        AgentSession session = sessionManagerProvider.get().getActiveSession();
        session.removeMessageById( messageId );
        applyToView( view -> view.removeMessage( messageId ) );
    }
}
