package com.rubberjam.eclipse.assistai.mcp.http;

import java.util.UUID;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jface.preference.IPreferenceStore;
import org.osgi.service.prefs.BackingStoreException;
import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.preferences.PreferenceConstants;
import jakarta.inject.Singleton;

/** Connection settings follow the Eclipse installation across workspace switches. */
@Creatable
@Singleton
public class HttpMcpServerPreferencesProvider
{
    private static final String[] KEYS = {
        PreferenceConstants.ASSISTAI_MCP_HTTP_HOSTNAME,
        PreferenceConstants.ASSISTAI_MCP_HTTP_PORT,
        PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN,
        PreferenceConstants.ASSISTAI_MCP_HTTP_ENABLED
    };

    private IEclipsePreferences preferences()
    {
        var plugin = Activator.getDefault();
        var preferences = ConfigurationScope.INSTANCE.getNode( plugin.getBundle().getSymbolicName() );
        migrate( preferences, plugin.getPreferenceStore() );
        return preferences;
    }

    static synchronized void migrate( IEclipsePreferences target, IPreferenceStore legacy )
    {
        boolean changed = false;
        for ( String key : KEYS )
        {
            if ( target.get( key, null ) == null )
            {
                String value = legacy.getString( key );
                if ( key.equals( PreferenceConstants.ASSISTAI_MCP_HTTP_AUTH_TOKEN )
                        && ( !legacy.contains( key ) || legacy.isDefault( key ) ) && value.isBlank() )
                {
                    value = UUID.randomUUID().toString();
                }
                target.put( key, value );
                changed = true;
            }
        }
        if ( changed )
        {
            flush( target );
        }
    }

    private static void flush( IEclipsePreferences preferences )
    {
        try
        {
            preferences.flush();
        }
        catch ( BackingStoreException e )
        {
            throw new IllegalStateException( "Cannot persist installation-wide MCP settings", e );
        }
    }

    public HttpMcpServerPreferences get()
    {
        var preferences = preferences();
        return new HttpMcpServerPreferences(
                preferences.getInt( KEYS[1], 27417 ),
                preferences.get( KEYS[0], "localhost" ),
                preferences.get( KEYS[2], "" ) );
    }

    public void save( HttpMcpServerPreferences settings )
    {
        if ( settings.port() < 1 || settings.port() > 65535 || settings.hostname() == null
                || settings.hostname().isBlank() )
        {
            throw new IllegalArgumentException( "MCP requires a hostname and port between 1 and 65535" );
        }
        var preferences = preferences();
        preferences.put( KEYS[0], settings.hostname().trim() );
        preferences.putInt( KEYS[1], settings.port() );
        preferences.put( KEYS[2], settings.token() == null ? "" : settings.token() );
        flush( preferences );
    }

    public boolean isEnabled()
    {
        return preferences().getBoolean( KEYS[3], true );
    }

    public void setEnabled( boolean enabled )
    {
        var preferences = preferences();
        preferences.putBoolean( KEYS[3], enabled );
        flush( preferences );
    }

    public void resetToDefaults()
    {
        // Preserve credentials so restoring defaults does not disconnect configured agents.
        save( new HttpMcpServerPreferences( 27417, "localhost", get().token() ) );
        setEnabled( true );
    }
}
