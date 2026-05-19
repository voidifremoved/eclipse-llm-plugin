package com.rubberjam.eclipse.assistai.view;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.rubberjam.eclipse.assistai.agent.AgentContextSnapshot;

/**
 * Collapsible sidebar showing workspace context for the agent (Phase 3.2).
 */
public class AgentContextPanel extends Composite
{
    private final Label projectValue;

    private final Label fileValue;

    private final Label selectionValue;

    private final Composite resourcesList;

    private Runnable addSelectionAction;

    public AgentContextPanel( Composite parent, int style )
    {
        super( parent, style );
        setLayout( new GridLayout( 1, false ) );

        Label header = new Label( this, SWT.NONE );
        header.setText( "Context" );
        header.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );

        new Label( this, SWT.NONE ).setText( "Project:" );
        projectValue = newLabelValue();

        new Label( this, SWT.NONE ).setText( "Open file:" );
        fileValue = newLabelValue();

        new Label( this, SWT.NONE ).setText( "Selection:" );
        selectionValue = newLabelValue();

        Button addSelection = new Button( this, SWT.PUSH );
        addSelection.setText( "Add selection to cache" );
        addSelection.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );
        addSelection.addListener( SWT.Selection, e -> {
            if ( addSelectionAction != null )
            {
                addSelectionAction.run();
            }
        } );

        Label resourcesHeader = new Label( this, SWT.NONE );
        resourcesHeader.setText( "Cached resources" );
        resourcesHeader.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );

        ScrolledComposite scroll = new ScrolledComposite( this, SWT.V_SCROLL | SWT.BORDER );
        scroll.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, true ) );
        scroll.setMinSize( 120, 80 );
        resourcesList = new Composite( scroll, SWT.NONE );
        resourcesList.setLayout( new GridLayout( 1, false ) );
        scroll.setContent( resourcesList );
        scroll.setExpandHorizontal( true );
        scroll.setExpandVertical( true );
    }

    private Label newLabelValue()
    {
        Label value = new Label( this, SWT.WRAP );
        value.setText( "(none)" );
        value.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, false ) );
        return value;
    }

    public void setAddSelectionAction( Runnable action )
    {
        this.addSelectionAction = action;
    }

    public void update( AgentContextSnapshot snapshot )
    {
        if ( isDisposed() )
        {
            return;
        }
        projectValue.setText( display( snapshot.projectName() ) );
        fileValue.setText( displayPath( snapshot.filePath(), snapshot.fileName() ) );
        selectionValue.setText( display( snapshot.selectionPreview() ) );
        for ( org.eclipse.swt.widgets.Control child : resourcesList.getChildren() )
        {
            child.dispose();
        }
        List<String> labels = snapshot.cachedResourceLabels();
        if ( labels.isEmpty() )
        {
            Label empty = new Label( resourcesList, SWT.NONE );
            empty.setText( "(none — Ctrl+Alt+A or drag files)" );
        }
        else
        {
            for ( String label : labels )
            {
                Label row = new Label( resourcesList, SWT.WRAP );
                row.setText( "• " + label );
                row.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );
            }
        }
        resourcesList.pack( true );
        layout( true, true );
    }

    private static String display( String value )
    {
        if ( value == null || value.isBlank() )
        {
            return "(none)";
        }
        String trimmed = value.trim();
        if ( trimmed.length() > 200 )
        {
            return trimmed.substring( 0, 200 ) + "…";
        }
        return trimmed;
    }

    private static String displayPath( String path, String name )
    {
        if ( path != null && !path.isBlank() )
        {
            return path;
        }
        if ( name != null && !name.isBlank() )
        {
            return name;
        }
        return "(none)";
    }
}
