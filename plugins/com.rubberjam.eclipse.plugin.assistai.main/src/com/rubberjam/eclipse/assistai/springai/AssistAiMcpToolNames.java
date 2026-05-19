package com.rubberjam.eclipse.assistai.springai;

import java.util.Set;

/**
 * AssistAI convention for MCP tool names exposed to Spring AI ({@code server__toolName}).
 */
public final class AssistAiMcpToolNames
{
    private static final String SEPARATOR = "__";

    private AssistAiMcpToolNames()
    {
    }

    public static String prefixed( String serverName, String toolName )
    {
        return serverName + SEPARATOR + toolName;
    }

    /**
     * Strips the AssistAI server prefix ({@code eclipse-ide__getSource} → {@code getSource}).
     */
    public static String bareToolName( String toolName )
    {
        if ( toolName == null )
        {
            return "";
        }
        int sep = toolName.indexOf( SEPARATOR );
        if ( sep > 0 && sep + SEPARATOR.length() < toolName.length() )
        {
            return toolName.substring( sep + SEPARATOR.length() );
        }
        return toolName;
    }

    /**
     * Whether a callback name is permitted by the allowlist (exact or bare-tool match).
     */
    public static boolean matchesAllowed( String callbackName, Set<String> allowed )
    {
        if ( allowed.contains( callbackName ) )
        {
            return true;
        }
        if ( callbackName.indexOf( SEPARATOR ) >= 0 )
        {
            return false;
        }
        String suffix = SEPARATOR + callbackName;
        for ( String allowedName : allowed )
        {
            if ( allowedName.endsWith( suffix ) )
            {
                return true;
            }
        }
        return false;
    }
}
