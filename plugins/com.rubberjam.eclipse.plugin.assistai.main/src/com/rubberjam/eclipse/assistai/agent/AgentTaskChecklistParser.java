package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses markdown task lists from assistant messages.
 */
public final class AgentTaskChecklistParser
{
    private AgentTaskChecklistParser()
    {
    }

    public static List<AgentTaskItem> parse( String markdown )
    {
        List<AgentTaskItem> items = new ArrayList<>();
        if ( markdown == null || markdown.isBlank() )
        {
            return items;
        }
        for ( String line : markdown.split( "\n" ) )
        {
            String trimmed = line.trim();
            if ( trimmed.length() < 6 )
            {
                continue;
            }
            if ( trimmed.startsWith( "- [" ) && trimmed.charAt( 3 ) != ']' )
            {
                char mark = trimmed.charAt( 3 );
                boolean done = mark == 'x' || mark == 'X';
                if ( trimmed.charAt( 4 ) == ']' && trimmed.length() > 6 )
                {
                    items.add( new AgentTaskItem( trimmed.substring( 6 ).trim(), done ) );
                }
            }
        }
        return items;
    }

    /**
     * Heuristic: mark tasks whose text mentions keywords from the tool name.
     */
    public static void markCompletedByTool( List<AgentTaskItem> items, String toolName )
    {
        if ( items == null || items.isEmpty() || toolName == null || toolName.isBlank() )
        {
            return;
        }
        String lowerTool = toolName.toLowerCase();
        for ( AgentTaskItem item : items )
        {
            if ( item.isDone() )
            {
                continue;
            }
            String lowerText = item.text().toLowerCase();
            if ( lowerText.isBlank() )
            {
                continue;
            }
            if ( toolMatchesTask( lowerTool, lowerText ) )
            {
                item.setDone( true );
            }
        }
    }

    private static boolean toolMatchesTask( String lowerTool, String lowerText )
    {
        if ( lowerTool.contains( "getcompilationerrors" ) || lowerTool.contains( "compile" ) )
        {
            return lowerText.contains( "compil" ) || lowerText.contains( "error" ) || lowerText.contains( "verify" );
        }
        if ( lowerTool.contains( "applypatch" ) || lowerTool.contains( "replacestring" )
                || lowerTool.contains( "executequickfix" ) || lowerTool.contains( "organizeimports" ) )
        {
            return lowerText.contains( "fix" ) || lowerText.contains( "edit" ) || lowerText.contains( "patch" )
                    || lowerText.contains( "import" );
        }
        if ( lowerTool.contains( "runmaven" ) || lowerTool.contains( "test" ) )
        {
            return lowerText.contains( "build" ) || lowerText.contains( "test" ) || lowerText.contains( "maven" );
        }
        if ( lowerTool.contains( "getclassoutline" ) || lowerTool.contains( "readproject" ) )
        {
            return lowerText.contains( "read" ) || lowerText.contains( "inspect" ) || lowerText.contains( "review" );
        }
        String bare = bareToolName( lowerTool );
        return bare.length() > 4 && lowerText.contains( bare );
    }

    private static String bareToolName( String lowerTool )
    {
        int sep = lowerTool.indexOf( "__" );
        if ( sep > 0 && sep + 2 < lowerTool.length() )
        {
            return lowerTool.substring( sep + 2 );
        }
        return lowerTool;
    }
}
