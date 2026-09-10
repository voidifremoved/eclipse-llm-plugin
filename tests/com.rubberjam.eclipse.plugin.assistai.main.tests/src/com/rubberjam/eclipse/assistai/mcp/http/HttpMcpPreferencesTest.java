package com.rubberjam.eclipse.assistai.mcp.http;

import static org.junit.jupiter.api.Assertions.*;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import org.eclipse.jface.preference.PreferenceStore;
import org.junit.jupiter.api.Test;
import com.rubberjam.eclipse.assistai.preferences.PreferenceConstants;

public class HttpMcpPreferencesTest
{
    private IEclipsePreferences sharedPreferences()
    {
        var values = new HashMap<String, String>();
        return (IEclipsePreferences) Proxy.newProxyInstance( getClass().getClassLoader(),
                new Class<?>[] { IEclipsePreferences.class }, (proxy, method, args) -> {
                    return switch ( method.getName() ) {
                        case "put" -> { values.put( (String) args[0], (String) args[1] ); yield null; }
                        case "get" -> values.getOrDefault( args[0], (String) args[1] );
                        case "getInt" -> values.containsKey( args[0] ) ? Integer.parseInt( values.get( args[0] ) ) : args[1];
                        case "flush" -> null;
                        default -> throw new UnsupportedOperationException( method.getName() );
                    };
                } );
    }

    private PreferenceStore workspace()
    {
        var store = new PreferenceStore();
        store.setDefault( PreferenceConstants.ASSISTAI_MCP_HTTP_HOSTNAME, "localhost" );
        store.setDefault( PreferenceConstants.ASSISTAI_MCP_HTTP_PORT, 27417 );
        store.setDefault( PreferenceConstants.ASSISTAI_MCP_HTTP_ENABLED, true );
        return store;
    }

    @Test public void switchingWorkspacePreservesConnectionAndToken()
    {
        var shared = sharedPreferences();
        var first = workspace();
        first.setValue( PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN, "existing-token" );
        first.setValue( PreferenceConstants.ASSISTAI_MCP_HTTP_PORT, 28000 );
        HttpMcpServerPreferencesProvider.migrate( shared, first );
        var second = workspace();
        second.setValue( PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN, "different-token" );
        HttpMcpServerPreferencesProvider.migrate( shared, second );
        assertEquals( "existing-token", shared.get( PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN, null ) );
        assertEquals( 28000, shared.getInt( PreferenceConstants.ASSISTAI_MCP_HTTP_PORT, 0 ) );
    }

    @Test public void freshWorkspaceGeneratesTokenOnlyOnce()
    {
        var shared = sharedPreferences();
        HttpMcpServerPreferencesProvider.migrate( shared, workspace() );
        String token = shared.get( PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN, "" );
        assertFalse( token.isBlank() );
        HttpMcpServerPreferencesProvider.migrate( shared, workspace() );
        assertEquals( token, shared.get( PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN, "" ) );
    }
}
