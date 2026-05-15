package com.rubberjam.eclipse.assistai.mcp.remote;

import java.net.URI;
import java.util.Objects;

/**
 * Parsed HTTP MCP endpoint: base URL (scheme + authority) and path used by the streamable HTTP transport.
 */
public record McpHttpEndpoint( String baseUrl, String endpointPath )
{
    private static final String DEFAULT_ENDPOINT_PATH = "/mcp";

    public McpHttpEndpoint
    {
        Objects.requireNonNull( baseUrl, "baseUrl" );
        Objects.requireNonNull( endpointPath, "endpointPath" );
    }

    public static McpHttpEndpoint parse( String url )
    {
        Objects.requireNonNull( url );
        String trimmed = url.trim();
        if ( trimmed.isEmpty() )
        {
            throw new IllegalArgumentException( "MCP URL cannot be empty" );
        }
        URI uri = URI.create( trimmed );
        if ( uri.getScheme() == null || uri.getHost() == null )
        {
            throw new IllegalArgumentException( "Invalid MCP URL (expected http(s)://host[:port]/path): " + url );
        }
        String base = uri.getScheme() + "://" + uri.getAuthority();
        String path = uri.getRawPath();
        if ( path == null || path.isEmpty() )
        {
            path = DEFAULT_ENDPOINT_PATH;
        }
        if ( uri.getRawQuery() != null && !uri.getRawQuery().isEmpty() )
        {
            path = path + "?" + uri.getRawQuery();
        }
        return new McpHttpEndpoint( base, path );
    }
}
