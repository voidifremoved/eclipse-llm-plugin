package com.rubberjam.eclipse.assistai.preferences.mcp;

import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor.McpServerDescriptorWithStatus;

/**
 * Debug logging for MCP server preference persistence (view in Eclipse Error Log).
 */
public final class McpServerPreferencesLog
{
    private static final String PREFIX = "[AssistAI MCP Prefs] ";

    private McpServerPreferencesLog()
    {
    }

    public static void info( String message )
    {
        log().info( PREFIX + message );
    }

    public static void warn( String message )
    {
        log().warn( PREFIX + message );
    }

    public static void error( String message, Throwable throwable )
    {
        log().error( PREFIX + message, throwable );
    }

    public static void error( String message )
    {
        log().error( PREFIX + message );
    }

    public static void logDescriptors( String label, List<McpServerDescriptor> servers )
    {
        if ( servers == null || servers.isEmpty() )
        {
            info( label + ": (empty)" );
            return;
        }
        StringBuilder sb = new StringBuilder( label ).append( " (" ).append( servers.size() ).append( "): " );
        for ( int i = 0; i < servers.size(); i++ )
        {
            McpServerDescriptor s = servers.get( i );
            if ( i > 0 )
            {
                sb.append( "; " );
            }
            sb.append( i ).append( '=' ).append( describe( s ) );
        }
        info( sb.toString() );
    }

    public static void logDescriptorsWithStatus( String label, List<McpServerDescriptorWithStatus> servers )
    {
        if ( servers == null || servers.isEmpty() )
        {
            info( label + ": (empty)" );
            return;
        }
        StringBuilder sb = new StringBuilder( label ).append( " (" ).append( servers.size() ).append( "): " );
        for ( int i = 0; i < servers.size(); i++ )
        {
            McpServerDescriptorWithStatus row = servers.get( i );
            if ( i > 0 )
            {
                sb.append( "; " );
            }
            sb.append( i ).append( '=' ).append( describe( row.descriptor() ) )
                    .append( " status=" ).append( row.status() );
        }
        info( sb.toString() );
    }

    public static String describe( McpServerDescriptor s )
    {
        if ( s == null )
        {
            return "null";
        }
        String url = s.url() != null ? s.url() : "";
        return s.name() + "{uid=" + s.uid()
                + ", builtIn=" + s.builtIn()
                + ", enabled=" + s.enabled()
                + ", http=" + s.isHttpServer()
                + ", url=" + abbreviate( url, 60 )
                + ", cmd=" + abbreviate( s.command(), 40 )
                + ", excludedTools=" + s.excludedTools().size()
                + "}";
    }

    private static String abbreviate( String text, int max )
    {
        if ( text == null )
        {
            return "";
        }
        if ( text.length() <= max )
        {
            return text;
        }
        return text.substring( 0, max ) + "...";
    }

    private static ILog log()
    {
        return Platform.getLog( Activator.getDefault().getBundle() );
    }
}
