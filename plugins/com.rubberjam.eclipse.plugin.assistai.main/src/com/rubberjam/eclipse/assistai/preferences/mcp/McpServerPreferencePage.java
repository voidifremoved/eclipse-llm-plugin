package com.rubberjam.eclipse.assistai.preferences.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor.EnvironmentVariable;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor.McpServerDescriptorWithStatus;

/**
 * Preference page for MCP Server settings
 */
public class McpServerPreferencePage extends PreferencePage implements IWorkbenchPreferencePage
{

    private UISynchronize                uiSync;

    private McpServerPreferencePresenter presenter;

    private CheckboxTableViewer          serverTableViewer;

    private Table                        serverTable;

    private Text                         nameText;

    private Text                         commandText;

    private Text                         urlText;

    private TableViewer                  envTableViewer;

    private Table                        envTable;

    private Group                        form;
    
    private Label                        nameLabel;
    
    private Label                        commandLabel;

    private Label                        urlLabel;

    private Button                       addButton;

    private Button                       removeButton;

    private Button                       addEnvButton;

    private Button                       removeEnvButton;

    private Button                       editEnvButton;

    private CheckboxTableViewer          toolTableViewer;

    private Table                        toolTable;

    private Label                        toolsLabel;

    private List<EnvironmentVariable>    currentEnvVars = new ArrayList<>();

    private boolean                      addingNewServer;

    private boolean                      suppressServerSelectionEvents;

    private Button                       workspacePresetButton;

    private Button                       allowWebToolsCheckbox;

    private Button                       useEclipseSkillsCheckbox;

    private Button                       verifyAfterEditCheckbox;

    private Text                         maxToolRoundsText;

    @Override
    public void init( IWorkbench workbench )
    {
        presenter = Activator.getDefault().getMCPServerPreferencePresenter();

        // Get UISynchronize service
        IEclipseContext eclipseContext = workbench.getService( IEclipseContext.class );
        uiSync = eclipseContext.get( UISynchronize.class );
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite root = new Composite( parent, SWT.NONE );
        root.setLayout( new GridLayout( 1, false ) );

        createAgentPolicySection( root );

        var sashForm = new SashForm( root, SWT.VERTICAL );
        sashForm.setLayoutData( new GridData( GridData.FILL, GridData.FILL, true, true ) );

        // Composite for the server list and buttons
        Composite listButtonsComposite = new Composite(sashForm, SWT.NONE);
        listButtonsComposite.setLayout(new GridLayout(2, false));

        // Create CheckboxTableViewer
        serverTableViewer = CheckboxTableViewer.newCheckList(listButtonsComposite, SWT.BORDER | SWT.FULL_SELECTION);
        serverTable = serverTableViewer.getTable();
        serverTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        serverTable.setHeaderVisible(true);
        serverTable.setLinesVisible(true);

        // Setup table columns
        createServerTableColumns(serverTableViewer);

        // Set content provider
        serverTableViewer.setContentProvider(ArrayContentProvider.getInstance());

        // Button composite for Add/Remove
        Composite buttonComposite = new Composite(listButtonsComposite, SWT.NONE);
        buttonComposite.setLayout(new GridLayout(1, false));
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));

        addButton = new Button(buttonComposite, SWT.NONE);
        addButton.setText("Add");
        addButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        removeButton = new Button(buttonComposite, SWT.NONE);
        removeButton.setText("Remove");
        removeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Create server details form
        createServerDetailsForm(sashForm);

        // Set SashForm weights
        sashForm.setWeights(new int[]{1, 2});

        // Register with presenter
        presenter.registerView(this);

        // Initialize listeners
        initializeListeners(serverTableViewer);
        initializeDetailsListeners();
        clearServerDetails();
        syncAgentPolicyControls();

        return root;
    }

    private void createAgentPolicySection( Composite parent )
    {
        Group agentGroup = new Group( parent, SWT.NONE );
        agentGroup.setText( "Agent tool policy" );
        agentGroup.setLayout( new GridLayout( 2, false ) );
        agentGroup.setLayoutData( new GridData( GridData.FILL, GridData.CENTER, true, false ) );

        workspacePresetButton = new Button( agentGroup, SWT.PUSH );
        workspacePresetButton.setText( "Apply workspace agent preset" );
        workspacePresetButton.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false, 2, 1 ) );
        workspacePresetButton.setToolTipText(
                "Enables Eclipse workspace MCP servers and memory; disables web search servers." );
        workspacePresetButton.addSelectionListener( new SelectionAdapter()
        {
            @Override
            public void widgetSelected( SelectionEvent e )
            {
                presenter.applyWorkspaceAgentPreset();
            }
        } );

        allowWebToolsCheckbox = new Button( agentGroup, SWT.CHECK );
        allowWebToolsCheckbox.setText( "Allow web search tools (duck-duck-search, webpage-reader)" );
        allowWebToolsCheckbox.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false, 2, 1 ) );

        useEclipseSkillsCheckbox = new Button( agentGroup, SWT.CHECK );
        useEclipseSkillsCheckbox.setText( "Include Eclipse workflow hints in agent system prompt" );
        useEclipseSkillsCheckbox.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false, 2, 1 ) );

        verifyAfterEditCheckbox = new Button( agentGroup, SWT.CHECK );
        verifyAfterEditCheckbox.setText( "Verify after edit (auto-run getCompilationErrors after eclipse-coder tools)" );
        verifyAfterEditCheckbox.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false, 2, 1 ) );

        Label maxRoundsLabel = new Label( agentGroup, SWT.NONE );
        maxRoundsLabel.setText( "Max tool rounds per message:" );
        maxToolRoundsText = new Text( agentGroup, SWT.BORDER );
        maxToolRoundsText.setToolTipText( "Stops the agent after this many tool calls in one user message (minimum 1)." );
        GridData maxRoundsData = new GridData( SWT.FILL, SWT.CENTER, true, false );
        maxRoundsData.widthHint = 80;
        maxToolRoundsText.setLayoutData( maxRoundsData );
    }

    public void syncAgentPolicyControls()
    {
        if ( allowWebToolsCheckbox == null || allowWebToolsCheckbox.isDisposed() )
        {
            return;
        }
        allowWebToolsCheckbox.setSelection( presenter.isAllowWebTools() );
        useEclipseSkillsCheckbox.setSelection( presenter.isUseEclipseSkillsInPrompt() );
        verifyAfterEditCheckbox.setSelection( presenter.isVerifyAfterEdit() );
        maxToolRoundsText.setText( String.valueOf( presenter.getMaxToolRounds() ) );
    }

    private boolean saveAgentPolicyFromControls()
    {
        if ( allowWebToolsCheckbox == null || allowWebToolsCheckbox.isDisposed() )
        {
            return true;
        }
        int maxRounds = parseMaxToolRounds( maxToolRoundsText.getText().trim() );
        if ( maxRounds < 1 )
        {
            showError( "Max tool rounds must be a positive integer." );
            return false;
        }
        presenter.saveAgentToolPreferences(
                allowWebToolsCheckbox.getSelection(),
                useEclipseSkillsCheckbox.getSelection(),
                verifyAfterEditCheckbox.getSelection(),
                maxRounds );
        return true;
    }

    private int parseMaxToolRounds( String text )
    {
        if ( text.isEmpty() )
        {
            return -1;
        }
        try
        {
            return Integer.parseInt( text );
        }
        catch ( NumberFormatException e )
        {
            return -1;
        }
    }

    private void createServerTableColumns(CheckboxTableViewer checkboxTableViewer) {
        // Enabled column
        TableViewerColumn useCol = new TableViewerColumn(checkboxTableViewer, SWT.NONE);
        useCol.getColumn().setText("Use");
        useCol.getColumn().setWidth(45);
        useCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return "";
            }
        });
        // Name column
        TableViewerColumn nameCol = new TableViewerColumn(checkboxTableViewer, SWT.NONE);
        nameCol.getColumn().setText("Name");
        nameCol.getColumn().setWidth(200);
        nameCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                var descriptor = (McpServerDescriptorWithStatus) element;
                var displayedName = descriptor.descriptor().name();
                if ( descriptor.descriptor().builtIn() )
                {
                    displayedName += " [built-in]";
                }
                return displayedName;
            }
        });

        // Status column
        TableViewerColumn statusCol = new TableViewerColumn(checkboxTableViewer, SWT.NONE);
        statusCol.getColumn().setText("Status");
        statusCol.getColumn().setWidth(100);
        statusCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((McpServerDescriptorWithStatus) element).status().name();
            }
        });
    }

    private void initializeListeners(CheckboxTableViewer checkboxTableViewer) {
        // Server table selection listener
        serverTable.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if ( suppressServerSelectionEvents )
                {
                    return;
                }
                Objects.requireNonNull(presenter);
                int selectedIndex = serverTable.getSelectionIndex();
                McpServerPreferencesLog.info( "serverTable selection: index=" + selectedIndex
                        + " addingNewServer=" + addingNewServer );
                presenter.setSelectedServer(selectedIndex);
            }
        });

        // Add and remove server buttons
        addButton.addListener(SWT.Selection, e -> presenter.addServer());
        removeButton.addListener(SWT.Selection, e -> presenter.removeServer(serverTable.getSelectionIndex()));

        // Handle checkbox state changes
        checkboxTableViewer.addCheckStateListener(event -> {
            boolean checked = event.getChecked();
            var element = event.getElement();
            int index = ((List<?>) serverTableViewer.getInput()).indexOf(element);
            if (index != -1) {
                presenter.toggleServerEnabled(index, checked);
            }
        });
    }

    private void createServerDetailsForm( Composite parent )
    {
        form = new Group( parent, SWT.NULL );
        form.setText( "MCP Server Details" );
        FormLayout formLayout = new FormLayout();
        form.setLayout( formLayout );

        // Server name label and field
        nameLabel = new Label(form, SWT.NONE);
        nameLabel.setText("Name:");
        FormData nameLabelData = new FormData();
        nameLabelData.top = new FormAttachment(0, 10);
        nameLabelData.left = new FormAttachment(0, 10);
        nameLabel.setLayoutData(nameLabelData);
        
        nameText = new Text(form, SWT.BORDER);
        nameText.setToolTipText("Server name should contain only letters, numbers, underscores and hyphens ([a-zA-Z0-9_-]). Names must be unique and must not match a built-in server name (e.g. eclipse-ide, memory).");
        FormData nameTextData = new FormData();
        nameTextData.top = new FormAttachment(nameLabel, 0, SWT.CENTER);
        nameTextData.left = new FormAttachment(0, 150);
        nameTextData.right = new FormAttachment(100, -10);
        nameText.setLayoutData(nameTextData);
        
        // Server command label and field
        commandLabel = new Label(form, SWT.NONE);
        commandLabel.setText("Command:");
        FormData commandLabelData = new FormData();
        commandLabelData.top = new FormAttachment(nameText, 10);
        commandLabelData.left = new FormAttachment(0, 10);
        commandLabel.setLayoutData(commandLabelData);
        
        commandText = new Text(form, SWT.BORDER);
        commandText.setToolTipText("Command to start the MCP server. Example: npx -y @modelcontextprotocol/server-everything dir");
        FormData commandTextData = new FormData();
        commandTextData.top = new FormAttachment(commandLabel, 0, SWT.CENTER);
        commandTextData.left = new FormAttachment(0, 150);
        commandTextData.right = new FormAttachment(100, -10);
        commandText.setLayoutData(commandTextData);

        // HTTP MCP URL label and field
        urlLabel = new Label( form, SWT.NONE );
        urlLabel.setText( "URL:" );
        FormData urlLabelData = new FormData();
        urlLabelData.top = new FormAttachment( commandText, 10 );
        urlLabelData.left = new FormAttachment( 0, 10 );
        urlLabel.setLayoutData( urlLabelData );

        urlText = new Text( form, SWT.BORDER );
        urlText.setToolTipText( "HTTP MCP server URL (streamable transport). Example: http://localhost:8125/mcp/calculator. "
                + "Leave empty for stdio servers started via Command. Tools are discovered when you leave this field." );
        FormData urlTextData = new FormData();
        urlTextData.top = new FormAttachment( urlLabel, 0, SWT.CENTER );
        urlTextData.left = new FormAttachment( 0, 150 );
        urlTextData.right = new FormAttachment( 100, -10 );
        urlText.setLayoutData( urlTextData );

        // Tools label
        toolsLabel = new Label( form, SWT.NONE );
        toolsLabel.setText( "Tools:" );
        FormData toolsLabelData = new FormData();
        toolsLabelData.top = new FormAttachment( urlText, 15 );
        toolsLabelData.left = new FormAttachment( 0, 10 );
        toolsLabel.setLayoutData( toolsLabelData );

        // Tools checkbox table
        toolTableViewer = CheckboxTableViewer.newCheckList( form, SWT.BORDER | SWT.V_SCROLL );
        toolTable = toolTableViewer.getTable();
        toolTable.setHeaderVisible( false );
        toolTable.setLinesVisible( false );

        FormData toolTableData = new FormData();
        toolTableData.top = new FormAttachment( toolsLabel, 5 );
        toolTableData.left = new FormAttachment( 0, 10 );
        toolTableData.right = new FormAttachment( 100, -10 );
        toolTableData.bottom = new FormAttachment( 50, 0 );
        toolTable.setLayoutData( toolTableData );

        toolTableViewer.setContentProvider( ArrayContentProvider.getInstance() );
        toolTableViewer.setLabelProvider( new LabelProvider() );

        // Environment variables label
        Label envLabel = new Label( form, SWT.NONE );
        envLabel.setText( "Environment Variables:" );
        FormData envLabelData = new FormData();
        envLabelData.top = new FormAttachment( toolTable, 10 );
        envLabelData.left = new FormAttachment( 0, 10 );
        envLabel.setLayoutData( envLabelData );

        // Environment variables table and buttons
        Composite envComposite = new Composite( form, SWT.NONE );
        GridLayout envLayout = new GridLayout( 2, false );
        envComposite.setLayout( envLayout );

        FormData envCompositeData = new FormData();
        envCompositeData.top = new FormAttachment( envLabel, 5 );
        envCompositeData.left = new FormAttachment( 0, 10 );
        envCompositeData.right = new FormAttachment( 100, -10 );
        envCompositeData.bottom = new FormAttachment( 100, -10 );
        envComposite.setLayoutData( envCompositeData );

        // Create environment variables table
        envTableViewer = new TableViewer( envComposite, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL );
        envTable = envTableViewer.getTable();
        envTable.setHeaderVisible( true );
        envTable.setLinesVisible( true );

        GridData envTableData = new GridData( SWT.FILL, SWT.FILL, true, true );
        envTable.setLayoutData( envTableData );

        // Environment variables table columns
        TableViewerColumn envNameCol = new TableViewerColumn( envTableViewer, SWT.NONE );
        envNameCol.getColumn().setText( "Name" );
        envNameCol.getColumn().setWidth( 150 );
        envNameCol.setLabelProvider( new ColumnLabelProvider()
        {
            @Override
            public String getText( Object element )
            {
                return ( (EnvironmentVariable) element ).name();
            }
        } );

        TableViewerColumn envValueCol = new TableViewerColumn( envTableViewer, SWT.NONE );
        envValueCol.getColumn().setText( "Value" );
        envValueCol.getColumn().setWidth( 250 );
        envValueCol.setLabelProvider( new ColumnLabelProvider()
        {
            @Override
            public String getText( Object element )
            {
                return ( (EnvironmentVariable) element ).value();
            }
        } );

        envTableViewer.setContentProvider( ArrayContentProvider.getInstance() );

        // Buttons for environment variables
        Composite envButtonsComposite = new Composite( envComposite, SWT.NONE );
        envButtonsComposite.setLayout( new GridLayout( 1, false ) );
        envButtonsComposite.setLayoutData( new GridData( SWT.FILL, SWT.TOP, false, false ) );

        addEnvButton = new Button( envButtonsComposite, SWT.NONE );
        addEnvButton.setText( "Add" );
        addEnvButton.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );

        editEnvButton = new Button( envButtonsComposite, SWT.NONE );
        editEnvButton.setText( "Edit" );
        editEnvButton.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );

        removeEnvButton = new Button( envButtonsComposite, SWT.NONE );
        removeEnvButton.setText( "Remove" );
        removeEnvButton.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );
    }

    private Text addTextField( Composite form, String labelText )
    {
        Label label = new Label( form, SWT.NONE );
        label.setText( labelText );
        FormData labelData = new FormData();
        Control[] children = form.getChildren();
        if ( children.length == 0 )
        {
            // First control, attach to top
            labelData.top = new FormAttachment( 0, 10 );
        }
        else
        {
            // Attach below the last control
            labelData.top = new FormAttachment( children[children.length - 1], 10 );
        }
        labelData.left = new FormAttachment( 0, 10 );
        label.setLayoutData( labelData );

        Text text = new Text( form, SWT.BORDER );
        FormData textData = new FormData();
        textData.left = new FormAttachment( 0, 150 );
        textData.right = new FormAttachment( 100, -10 );
        textData.top = new FormAttachment( label, 0, SWT.CENTER );
        text.setLayoutData( textData );
        return text;
    }

    private void initializeDetailsListeners()
    {
        urlText.addFocusListener( new FocusAdapter()
        {
            @Override
            public void focusLost( FocusEvent e )
            {
                presenter.discoverToolsFromUrl( urlText.getText().trim(), currentEnvVars );
            }
        } );

        // Tool checkbox listener
        toolTableViewer.addCheckStateListener( event -> {
            String toolName = (String) event.getElement();
            boolean checked = event.getChecked();
            int serverIndex = serverTable.getSelectionIndex();
            if ( serverIndex != -1 )
            {
                presenter.toggleToolEnabled( serverIndex, toolName, checked );
            }
        } );

        // Environment variables buttons
        addEnvButton.addListener( SWT.Selection, e -> {
            String[] result = openEnvironmentVariableDialog( "Add Environment Variable", "", "" );
            if ( result != null )
            {
                currentEnvVars.add( new EnvironmentVariable( result[0], result[1] ) );
                envTableViewer.setInput( currentEnvVars );
                envTableViewer.refresh();
            }
        } );

        editEnvButton.addListener( SWT.Selection, e -> {
            int selectedIndex = envTable.getSelectionIndex();
            if ( selectedIndex >= 0 )
            {
                EnvironmentVariable selectedVar = currentEnvVars.get( selectedIndex );
                String[] result = openEnvironmentVariableDialog( "Edit Environment Variable", selectedVar.name(), selectedVar.value() );
                if ( result != null )
                {
                    currentEnvVars.set( selectedIndex, new EnvironmentVariable( result[0], result[1] ) );
                    envTableViewer.setInput( currentEnvVars );
                    envTableViewer.refresh();
                }
            }
        } );

        removeEnvButton.addListener( SWT.Selection, e -> {
            int selectedIndex = envTable.getSelectionIndex();
            if ( selectedIndex >= 0 )
            {
                currentEnvVars.remove( selectedIndex );
                envTableViewer.setInput( currentEnvVars );
                envTableViewer.refresh();
            }
        } );
    }

    /**
     * Open a dialog to add or edit an environment variable
     * 
     * @param title
     *            the dialog title
     * @param initialName
     *            the initial name value
     * @param initialValue
     *            the initial value
     * @return array with name and value or null if canceled
     */
    private String[] openEnvironmentVariableDialog(String title, String initialName, String initialValue) 
    {
        EnvironmentVariableDialog dialog = new EnvironmentVariableDialog(getShell(), initialName, initialValue);
        if (dialog.open() == Window.OK) 
        {
            return dialog.getValues();
        }
        return null;
    }

    @Override
    protected void performApply()
    {
        McpServerPreferencesLog.info( "performApply: invoked" );
        if ( !saveAgentPolicyFromControls() )
        {
            return;
        }
        if ( commitServerDetails() )
        {
            super.performApply();
        }
        else
        {
            McpServerPreferencesLog.warn( "performApply: commitServerDetails returned false" );
        }
    }

    @Override
    public boolean performOk()
    {
        McpServerPreferencesLog.info( "performOk: invoked" );
        if ( !saveAgentPolicyFromControls() )
        {
            return false;
        }
        if ( !commitServerDetails() )
        {
            McpServerPreferencesLog.warn( "performOk: commitServerDetails returned false" );
            return false;
        }
        return super.performOk();
    }

    /**
     * Validates the details form and persists the server when adding or editing.
     *
     * @return {@code false} if validation failed
     */
    private boolean commitServerDetails()
    {
        String serverName = nameText.getText().trim();
        int selectionIndex = serverTable.getSelectionIndex();
        McpServerPreferencesLog.info( "commitServerDetails: name='" + serverName
                + "' addingNewServer=" + addingNewServer
                + " selectionIndex=" + selectionIndex );

        if ( serverName.isEmpty() )
        {
            if ( addingNewServer )
            {
                showError( "Server name cannot be empty" );
                return false;
            }
            McpServerPreferencesLog.info( "commitServerDetails: skip save (empty name, not adding)" );
            return true;
        }

        if ( !serverName.matches( "[a-zA-Z0-9_-]+" ) )
        {
            showError( "Server name can only contain letters, numbers, underscores and hyphens" );
            return false;
        }

        String url = urlText.getText().trim();
        String command = commandText.getText().trim();
        if ( url.isEmpty() && command.isEmpty() )
        {
            showError( "Either URL (HTTP MCP) or Command (stdio MCP) must be set" );
            return false;
        }

        List<String> excludedTools = collectExcludedTools();
        McpServerDescriptor updatedServer = new McpServerDescriptor( "",
                serverName,
                command,
                currentEnvVars,
                true,
                false,
                excludedTools,
                url );

        boolean isNewServer = addingNewServer || selectionIndex < 0;
        if ( !isNewServer && selectionIndex >= 0 )
        {
            Object input = serverTableViewer.getInput();
            if ( input instanceof List<?> rows && selectionIndex < rows.size()
                    && rows.get( selectionIndex ) instanceof McpServerDescriptorWithStatus row )
            {
                McpServerDescriptor selected = row.descriptor();
                if ( selected.builtIn() && !selected.name().equals( serverName ) )
                {
                    McpServerPreferencesLog.info( "commitServerDetails: treating as new server (form name '"
                            + serverName + "' differs from selected built-in '" + selected.name() + "')" );
                    isNewServer = true;
                }
            }
        }
        int displayIndex = isNewServer ? -1 : selectionIndex;
        McpServerPreferencesLog.info( "commitServerDetails: isNewServer=" + isNewServer
                + " displayIndex=" + displayIndex );
        if ( !presenter.saveServer( isNewServer, displayIndex, updatedServer ) )
        {
            return false;
        }
        return true;
    }

    public boolean isAddingNewServer()
    {
        return addingNewServer;
    }

    public void prepareAddServer()
    {
        McpServerPreferencesLog.info( "prepareAddServer" );
        addingNewServer = true;
        Runnable clearSelection = () -> serverTable.deselectAll();
        if ( getShell() != null && getShell().getDisplay() != null )
        {
            getShell().getDisplay().syncExec( clearSelection );
        }
        else
        {
            clearSelection.run();
        }
        nameText.setText( "" );
        commandText.setText( "" );
        urlText.setText( "" );
        currentEnvVars.clear();
        envTableViewer.setInput( currentEnvVars );
        envTableViewer.refresh();
        toolTableViewer.setInput( Collections.emptyList() );
        toolTableViewer.refresh();
        setDetailsEditable( true );
    }

    public void setAddingNewServer( boolean addingNewServer )
    {
        this.addingNewServer = addingNewServer;
    }

    @Override
    protected void performDefaults()
    {
        super.performDefaults();
        presenter.onPerformDefaults();
    }

    /**
     * Show the list of MCP servers
     * 
     * @param servers
     *            the servers to show
     */
    public void showServers(List<McpServerDescriptorWithStatus> servers) 
    {
        McpServerPreferencesLog.logDescriptorsWithStatus( "showServers: requested", servers );
        Runnable update = () -> {
            if ( serverTableViewer == null || serverTableViewer.getControl().isDisposed() )
            {
                McpServerPreferencesLog.warn( "showServers: table disposed, skipping UI update" );
                return;
            }

            suppressServerSelectionEvents = true;
            try
            {
                serverTableViewer.setInput( servers );
                serverTableViewer.refresh();

                for ( McpServerDescriptorWithStatus server : servers )
                {
                    serverTableViewer.setChecked( server, server.descriptor().enabled() );
                }
                serverTable.deselectAll();
                McpServerPreferencesLog.info( "showServers: table updated, rowCount="
                        + ( servers != null ? servers.size() : 0 )
                        + " selectionIndex=" + serverTable.getSelectionIndex() );
            }
            finally
            {
                suppressServerSelectionEvents = false;
            }
        };
        if ( Display.getCurrent() != null )
        {
            update.run();
        }
        else
        {
            uiSync.asyncExec( update );
        }
    }

    /**
     * Show the details of a server
     * 
     * @param serverDescriptor
     *            the server descriptor
     */
    public void showServerDetails( McpServerDescriptor serverDescriptor )
    {
        uiSync.asyncExec( () -> {
        	if (nameText == null || nameText.isDisposed())
        	{
        		return;
        	}
            nameText.setText( serverDescriptor.name() );
            commandText.setText( serverDescriptor.command() );
            urlText.setText( serverDescriptor.url() != null ? serverDescriptor.url() : "" );

            // Update environment variables
            currentEnvVars = new ArrayList<>( serverDescriptor.environmentVariables() );
            envTableViewer.setInput( currentEnvVars );
            envTableViewer.refresh();
        } );
    }

    /**
     * Clear the server details form
     */
    public void clearServerDetails()
    {
        uiSync.asyncExec( () -> {
        	if ( nameText == null || nameText.isDisposed() ) 
            {
                return;
            }
        	
            nameText.setText( "" );
            commandText.setText( "" );
            urlText.setText( "" );

            // Clear tools
            toolTableViewer.setInput( Collections.emptyList() );
            toolTableViewer.refresh();

            // Clear environment variables
            currentEnvVars.clear();
            envTableViewer.setInput( currentEnvVars );
            envTableViewer.refresh();
        } );
    }

    public void showToolList( List<String> allTools, List<String> excludedTools )
    {
        uiSync.asyncExec( () -> {
            if ( toolTableViewer == null || toolTableViewer.getControl().isDisposed() )
            {
                return;
            }
            toolTableViewer.setInput( allTools );
            for ( String tool : allTools )
            {
                toolTableViewer.setChecked( tool, !excludedTools.contains( tool ) );
            }
            toolTableViewer.refresh();
        } );
    }

    public void setToolsDiscoveryInProgress( boolean inProgress )
    {
        uiSync.asyncExec( () -> {
            if ( toolTable == null || toolTable.isDisposed() )
            {
                return;
            }
            toolTable.setEnabled( !inProgress );
            if ( toolsLabel != null && !toolsLabel.isDisposed() )
            {
                toolsLabel.setText( inProgress ? "Tools: (discovering…)" : "Tools:" );
            }
        } );
    }

    private List<String> collectExcludedTools()
    {
        List<String> excluded = new ArrayList<>();
        Object input = toolTableViewer.getInput();
        if ( input instanceof List<?> tools )
        {
            for ( Object element : tools )
            {
                String toolName = (String) element;
                if ( !toolTableViewer.getChecked( toolName ) )
                {
                    excluded.add( toolName );
                }
            }
        }
        return excluded;
    }

    /**
     * Set whether the remove button is enabled
     * @param enabled
     */
    public void setRemoveEditable( boolean enabled )
    {
        if ( removeButton == null || removeButton.isDisposed() )
        {
            return;
        }
        removeButton.setEnabled( enabled );
    }

    /**
     * Set whether the details form is editable
     * 
     * @param editable
     *            whether the form is editable
     */
    public void setDetailsEditable( boolean editable )
    {
        uiSync.asyncExec( () -> {
        	
        	if ( nameLabel == null || nameLabel.isDisposed() ) 
            {
                return;
            }
        	
            // Enable/disable all form controls
            nameLabel.setEnabled(editable);
            nameText.setEnabled(editable);
            commandLabel.setEnabled(editable);
            commandText.setEnabled(editable);
            urlLabel.setEnabled( editable );
            urlText.setEnabled( editable );

            // Tools stay enabled even for built-in servers
            toolsLabel.setEnabled( true );
            toolTable.setEnabled( true );

            // Additionally, enable/disable the environment variable controls
            if ( envTableViewer != null )
            {
                envTable.setEnabled( editable );
                addEnvButton.setEnabled( editable );
                editEnvButton.setEnabled( editable );
                removeEnvButton.setEnabled( editable );
            }

            if ( editable )
            {
                nameText.forceFocus();
                form.redraw();
                form.update();
            }
        } );
    }

    /**
     * Clear the server selection
     */
    public void clearServerSelection()
    {
        uiSync.asyncExec( () -> {
            if ( serverTable == null || serverTable.isDisposed() )
            {
                return;
            }
            serverTable.deselectAll();
        } );
    }

    /**
     * Show an error message
     * 
     * @param message
     *            the error message
     */
    public void showError( String message )
    {
        uiSync.asyncExec( () -> {
            if ( getShell() == null || getShell().isDisposed() )
            {
                return;
            }
            MessageDialog.openError( getShell(), "Error", message );
        } );
    }
    
    
    public class EnvironmentVariableDialog extends Dialog {
        private String name;
        private String value;
        private Text nameText;
        private Text valueText;

        public EnvironmentVariableDialog(Shell parentShell, String initialName, String initialValue) 
        {
            super(parentShell);
            this.name = initialName;
            this.value = initialValue;
            setTitle( "Enviromnet variable" );
        }

        @Override
        protected Control createDialogArea(Composite parent)
        {
            Composite container = (Composite) super.createDialogArea(parent);
            container.setLayout(new GridLayout(2, false));

            Label nameLabel = new Label(container, SWT.NONE);
            nameLabel.setText("Name:");
            nameText = new Text(container, SWT.BORDER);
            nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            nameText.setText(name != null ? name : "");

            Label valueLabel = new Label(container, SWT.NONE);
            valueLabel.setText("Value:");
            valueText = new Text(container, SWT.BORDER);
            valueText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            valueText.setText(value != null ? value : "");

            return container;
        }

        @Override
        protected void okPressed() {
            name = nameText.getText();
            value = valueText.getText();
            super.okPressed();
        }
        @Override
        protected Point getInitialSize() 
        {
            return new Point(400, 200); 
        }


        public String[] getValues() {
            return new String[] { name, value };
        }
    }
}
