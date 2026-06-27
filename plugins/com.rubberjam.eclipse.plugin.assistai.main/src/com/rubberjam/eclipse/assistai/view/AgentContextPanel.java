package com.rubberjam.eclipse.assistai.view;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
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

    private ScrolledComposite resourcesScroll;

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

        resourcesScroll = new ScrolledComposite( this, SWT.V_SCROLL | SWT.BORDER );
        resourcesScroll.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, true ) );
        resourcesScroll.setMinSize( 120, 80 );
        resourcesList = new Composite( resourcesScroll, SWT.NONE );
        resourcesList.setLayout( new GridLayout( 1, false ) );
        resourcesScroll.setContent( resourcesList );
        resourcesScroll.setExpandHorizontal( true );
        resourcesScroll.setExpandVertical( true );
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
        setLabelText( projectValue, displaySingleLine( snapshot.projectName(), 36 ), snapshot.projectName() );
        setLabelText( fileValue, displayPath( snapshot.filePath(), snapshot.fileName() ), snapshot.filePath() );
        setLabelText( selectionValue, displaySelection( snapshot.selectionPreview() ), snapshot.selectionPreview() );
        for ( org.eclipse.swt.widgets.Control child : resourcesList.getChildren() )
        {
            child.dispose();
        }
        List<String> labels = snapshot.cachedResourceLabels();
        if ( labels.isEmpty() )
        {
            Label empty = new Label( resourcesList, SWT.NONE );
            empty.setText( "(none - Ctrl+Alt+A or drag files)" );
        }
        else
        {
            for ( String label : labels )
            {
                Label row = new Label( resourcesList, SWT.WRAP );
                row.setText( "- " + displaySingleLine( label, 48 ) );
                row.setToolTipText( label );
                row.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ) );
            }
        }
        Point resourcesSize = resourcesList.computeSize( SWT.DEFAULT, SWT.DEFAULT );
        resourcesScroll.setMinSize( resourcesSize );
        layout( true, true );
    }

    private static void setLabelText( Label label, String value, String tooltip )
    {
        label.setText( value );
        label.setToolTipText( tooltip != null && !tooltip.isBlank() ? tooltip : value );
    }

    private static String displaySingleLine( String value, int maxLength )
    {
        if ( value == null || value.isBlank() )
        {
            return "(none)";
        }
        String trimmed = value.trim().replace( '\r', ' ' ).replace( '\n', ' ' );
        while ( trimmed.contains( "  " ) )
        {
            trimmed = trimmed.replace( "  ", " " );
        }
        if ( trimmed.length() > maxLength )
        {
            return trimmed.substring( 0, Math.max( 0, maxLength - 3 ) ) + "...";
        }
        return trimmed;
    }

    private static String displayPath( String path, String name )
    {
        if ( path != null && !path.isBlank() )
        {
            String normalized = path.replace( '\\', '/' );
            if ( normalized.length() <= 42 )
            {
                return normalized;
            }
            int lastSlash = normalized.lastIndexOf( '/' );
            String fileName = lastSlash >= 0 ? normalized.substring( lastSlash + 1 ) : normalized;
            if ( fileName.length() >= 38 )
            {
                return "..." + fileName.substring( fileName.length() - 38 );
            }
            return ".../" + fileName;
        }
        if ( name != null && !name.isBlank() )
        {
            return displaySingleLine( name, 42 );
        }
        return "(none)";
    }

    private static String displaySelection( String selection )
    {
        if ( selection == null || selection.isBlank() )
        {
            return "(none)";
        }
        String compact = selection.trim().replace( '\r', ' ' ).replace( '\n', ' ' );
        while ( compact.contains( "  " ) )
        {
            compact = compact.replace( "  ", " " );
        }
        return displaySingleLine( compact, 80 );
    }
}
