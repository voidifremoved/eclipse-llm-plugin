package com.rubberjam.eclipse.assistai.preferences.models;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;


public class ModelListPreferencePage extends PreferencePage implements IWorkbenchPreferencePage
{
    private UISynchronize uiSync;
    
    private ModelListPreferencePresenter presenter;
    
    private Table      modelTable;

    private Text       apiUrl;

    private Text       apiKey;

    private Text       connectionTimeout;

    private Text       requestTimeout;

    private Text       modelName;

    private Button     withVision;

    private Button     withFunctionCalls;

    private Text       withTemperature;

    private Group      form;

    private Button     addButton;

    private Button     removeButton;

    private int        lastSelectedIndex = -1;

    public ModelListPreferencePage()
    {
        setPreferenceStore( Activator.getDefault().getPreferenceStore() );
    }

    @Override
    public void init( IWorkbench workbench )
    {
        presenter = Activator.getDefault().getModelsPreferencePresenter();
        
        // workaroud to get UISynchronize as PreferencePage does not seem to
        // be handled by the eclipse context
        IEclipseContext eclipseContext = workbench.getService( IEclipseContext.class );
        uiSync = eclipseContext.get( UISynchronize.class );
    }

    @Override
    protected Control createContents( Composite parent )
    {
        // Change orientation to HORIZONTAL for side-by-side layout
        var sashForm = new SashForm( parent, SWT.VERTICAL );
        sashForm.setLayoutData( new GridData( GridData.FILL_BOTH ) );

        // Composite for list and buttons
        Composite listButtonsComposite = new Composite( sashForm, SWT.NONE );
        listButtonsComposite.setLayout( new GridLayout( 2, false ) );

        modelTable = new Table(listButtonsComposite, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL ); 
        modelTable.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, true ) );
        modelTable.setHeaderVisible( true );
        Stream.of( "Url", "Model Name" ).forEach( columnName -> {
           TableColumn column = new TableColumn(modelTable, SWT.NULL);
           column.setText(columnName);
        });
                
        // Composite for buttons to align them vertically
        Composite buttonComposite = new Composite( listButtonsComposite, SWT.NONE );
        buttonComposite.setLayout( new GridLayout( 1, false ) );
        buttonComposite.setLayoutData( new GridData( SWT.FILL, SWT.TOP, false, false ) );

        addButton = new Button( buttonComposite, SWT.NONE );
        addButton.setText( "Add" );
        addButton.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );

        removeButton = new Button( buttonComposite, SWT.NONE );
        removeButton.setText( "Remove" );
        removeButton.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );

        // Model details in the bottom part of the sash form
        createModelDetails( sashForm );

        // Adjust the weights to allocate space (e.g., 30% for list and buttons,
        // 70% for details)
        sashForm.setWeights( new int[] { 1, 2 } );

        presenter.registerView( this );

        initializeListeners();
        clearModelDetails();

        return sashForm;
    }

    @Override
    protected void performApply()
    {
        int selectedIndex = modelTable.getSelectionIndex();
        if ( selectedIndex >= 0 )
        {
            commitModelAt( selectedIndex, false );
            lastSelectedIndex = selectedIndex;
        }
        super.performApply();
    }

    @Override
    public boolean performOk()
    {
        int selectedIndex = modelTable.getSelectionIndex();
        if ( selectedIndex >= 0 )
        {
            commitModelAt( selectedIndex, false );
            lastSelectedIndex = selectedIndex;
        }
        return super.performOk();
    }

    @Override
    protected void performDefaults()
    {
        super.performDefaults();
        presenter.onPerformDefaults();
    }

    private void initializeListeners()
    {
        modelTable.addSelectionListener( new SelectionAdapter()
        {
            @Override
            public void widgetSelected( SelectionEvent e )
            {
                Objects.requireNonNull( presenter );
                int selectedIndex = modelTable.getSelectionIndex();
                if ( lastSelectedIndex >= 0 && lastSelectedIndex != selectedIndex )
                {
                    commitModelAt( lastSelectedIndex, true );
                }
                lastSelectedIndex = selectedIndex;
                presenter.setSelectedModel( selectedIndex );
            }
        } );    
        addButton.addListener( SWT.Selection, e -> presenter.addModel() );
        removeButton.addListener( SWT.Selection, e -> presenter.removeModel( modelTable.getSelectionIndex() ) );
        
    }

    private Composite createModelDetails( Composite parent )
    {
        
        form = new Group( parent, SWT.NULL );
        form.setText( "Model API" );
        FormLayout formLayout = new FormLayout();
        form.setLayout( formLayout );

        apiUrl = addTextField( form, "API Url:");
        apiKey = addTextField( form, "API Key:");
        connectionTimeout = addTextField( form, "Connection Timeout (s):");
        requestTimeout = addTextField( form, "Request Timeout (s):");
        modelName = addTextField( form, "Model Name:");
        withVision = addCheckField( form, "With Vision:");
        withFunctionCalls = addCheckField( form, "With Function Calls:");
        withTemperature = addTextField( form, "Temperature (0–2, -1 = provider default):" );

        return form;
    }

    private Button addCheckField( Composite form, String labelText)
    {
        Button button = new Button( form, SWT.CHECK );
        addFormControl( button, form, labelText);
        return button;
    }

    private Text addTextField( Composite form, String labelText)
    {
        Text text = new Text( form, SWT.BORDER );
        addFormControl( text, form, labelText);
        return text;
    }

    private Control addFormControl( Control control, Composite form, String labelText)
    {
        Label label = new Label( form, SWT.NONE );
        label.setText( labelText );
        FormData labelData = new FormData();
        Control[] children = form.getChildren();
        if ( children.length == 2 )
        {
            // First control, so attach it to the top of the form
            labelData.top = new FormAttachment( 0, 10 );
        }
        else
        {
            // Attach it below the last control
            Control lastControl = children[children.length-3];
            labelData.top = new FormAttachment( lastControl, 10 );
        }
        labelData.left = new FormAttachment( 0, 10 );
        label.setLayoutData( labelData );

        FormData textData = new FormData();
        textData.left = new FormAttachment( 0, 150 );
        textData.right = new FormAttachment( 100, -10 );
        textData.top = new FormAttachment( label, -2, SWT.TOP );
        control.setLayoutData( textData );
        return control;
    }

    public void showModels( java.util.List<ModelApiDescriptor> models )
    {
        uiSync.asyncExec( () -> {
            if ( modelTable == null || modelTable.isDisposed() )
            {
                return;
            }
            modelTable.removeAll();
            modelTable.clearAll();
            modelTable.deselectAll();
            models.stream().forEach( this::addToModelList );
            Arrays.stream( modelTable.getColumns() ).forEach( TableColumn::pack );
            modelTable.redraw();
            modelTable.update();
        } );
    }
    
    private void addToModelList( ModelApiDescriptor item )
    {
        TableItem tableItem = new TableItem( modelTable, SWT.NULL );
        tableItem.setText( 0, item.apiUrl() );
        tableItem.setText( 1, item.modelName() );
    }

    public void showModelDetails( ModelApiDescriptor modelApiDescriptor )
    {
        uiSync.asyncExec( () -> {
            if ( apiUrl == null || apiUrl.isDisposed() )
            {
                return;
            }
            apiUrl.setText( modelApiDescriptor.apiUrl() );
            apiKey.setText( modelApiDescriptor.apiKey() );
            connectionTimeout.setText( String.valueOf(modelApiDescriptor.connectionTimeoutSeconds()) );
            requestTimeout.setText( String.valueOf(modelApiDescriptor.requestTimeoutSeconds()) );
            modelName.setText( modelApiDescriptor.modelName() );
            withTemperature.setText( formatTemperatureForUi( modelApiDescriptor ) );
            withVision.setSelection( modelApiDescriptor.vision() );
            withFunctionCalls.setSelection( modelApiDescriptor.functionCalling() );
        } );
        setDetailsEditable( true );
    }

    public void clearModelDetails()
    {
        uiSync.asyncExec( () -> {
            if ( apiUrl == null || apiUrl.isDisposed() )
            {
                return;
            }
            apiUrl.setText( "" );
            apiKey.setText( "" );
            connectionTimeout.setText( "10" );
            requestTimeout.setText( "30" );
            modelName.setText( "" );
            withTemperature.setText( "0.7" );
            withVision.setSelection( false );
            withFunctionCalls.setSelection( false );
        } );
        setDetailsEditable( false );
    }
    
    public void setDetailsEditable( boolean editable )
    {
        uiSync.asyncExec( () -> {
            if ( form == null || form.isDisposed() )
            {
                return;
            }
            Arrays.stream( form.getChildren() )
                  .forEach( control -> control.setEnabled( editable ) );
            if ( editable )
            {
                apiUrl.forceFocus();
                form.redraw();
                form.update();
            }
        } );
    }

    public void clearModelSelection()
    {
        uiSync.asyncExec( () -> {
            if ( modelTable == null || modelTable.isDisposed() )
            {
                return;
            }
            modelTable.deselectAll();
            lastSelectedIndex = -1;
        } );
    }

    public void selectAndShowModel( int index, ModelApiDescriptor model )
    {
        uiSync.asyncExec( () -> {
            if ( modelTable == null || modelTable.isDisposed() )
            {
                return;
            }
            if ( index >= 0 && index < modelTable.getItemCount() )
            {
                modelTable.select( index );
                lastSelectedIndex = index;
            }
            showModelDetails( model );
        } );
    }

    private void commitModelAt( int selectedIndex, boolean quiet )
    {
        if ( selectedIndex < 0 || apiUrl == null || apiUrl.isDisposed() )
        {
            return;
        }
        String apiType = presenter.findByIndex( selectedIndex )
                .map( ModelApiDescriptor::apiType )
                .orElse( "openai" );
        ModelApiDescriptor updatedModel = new ModelApiDescriptor(
                "",
                apiType,
                apiUrl.getText(),
                apiKey.getText(),
                parseTimeout( connectionTimeout.getText(), 10 ),
                parseTimeout( requestTimeout.getText(), 30 ),
                modelName.getText(),
                parseTemperature( withTemperature.getText() ),
                withVision.getSelection(),
                withFunctionCalls.getSelection() );
        if ( quiet )
        {
            presenter.saveModelQuietly( selectedIndex, updatedModel );
        }
        else
        {
            presenter.saveModel( selectedIndex, updatedModel );
        }
    }

    private static int parseTimeout(String text, int defaultValue)
    {
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static float parseTemperature( String text )
    {
        try
        {
            return Float.parseFloat( text.trim() );
        }
        catch ( NumberFormatException e )
        {
            return 0.7f;
        }
    }

    private static String formatTemperatureForUi( ModelApiDescriptor descriptor )
    {
        if ( descriptor.temperature() < 0 )
        {
            return "-1";
        }
        return descriptor.scaledTemperature()
                .map( ModelListPreferencePage::formatTemperature )
                .orElse( "-1" );
    }

    private static String formatTemperature( float value )
    {
        if ( value == (float) (int) value )
        {
            return String.valueOf( (int) value );
        }
        return String.valueOf( value );
    }
}
