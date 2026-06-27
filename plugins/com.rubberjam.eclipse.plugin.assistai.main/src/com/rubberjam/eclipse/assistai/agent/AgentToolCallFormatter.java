package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats tool-call details for the agent chat UI (Phase 3.3).
 */
public final class AgentToolCallFormatter
{
    private static final int MAX_DETAILS_CHARS = 4_000;

    private static final Pattern WORKSPACE_PATH = Pattern.compile(
            "(/[A-Za-z0-9_.-]+)+\\.(java|xml|properties|gradle|kt|md|json|yaml|yml|txt)" );

    private AgentToolCallFormatter()
    {
    }

    public static String truncateDetails( String details )
    {
        if ( details == null )
        {
            return "";
        }
        if ( details.length() <= MAX_DETAILS_CHARS )
        {
            return details;
        }
        return details.substring( 0, MAX_DETAILS_CHARS )
                + "\n\n… (" + ( details.length() - MAX_DETAILS_CHARS ) + " more characters truncated)";
    }

    public static String formatDuration( long startMillis, long endMillis )
    {
        if ( startMillis <= 0 || endMillis <= 0 )
        {
            return "";
        }
        long ms = Math.max( 0, endMillis - startMillis );
        if ( ms < 1000 )
        {
            return ms + " ms";
        }
        return String.format( "%.1f s", ms / 1000.0 );
    }

    public static List<String> extractOpenablePaths( String details )
    {
        Set<String> paths = new LinkedHashSet<>();
        if ( details == null || details.isBlank() )
        {
            return List.of();
        }
        Matcher matcher = WORKSPACE_PATH.matcher( details );
        while ( matcher.find() )
        {
            paths.add( matcher.group() );
        }
        return new ArrayList<>( paths );
    }

    public static String preferencePageHint( String toolName )
    {
        if ( toolName == null || toolName.isBlank() )
        {
            return "";
        }
        if ( toolName.contains( "eclipse-coder" ) || toolName.contains( "eclipse-ide" )
                || toolName.contains( "eclipse-runner" ) || toolName.contains( "eclipse-git" ) )
        {
            return "Window → Preferences → Assist Agent → MCP Servers";
        }
        return "";
    }
}
