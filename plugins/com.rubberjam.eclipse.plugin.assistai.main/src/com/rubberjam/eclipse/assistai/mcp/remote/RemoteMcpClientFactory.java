package com.rubberjam.eclipse.assistai.mcp.remote;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.schema.jackson2.JacksonJsonSchemaValidatorSupplier;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Creates short-lived HTTP MCP clients for tool discovery and persistent clients for configured URL servers.
 */
public final class RemoteMcpClientFactory
{
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds( 15 );

    private RemoteMcpClientFactory()
    {
    }

    public static McpSyncClient createHttpClient( String url, Map<String, String> headers )
    {
        Objects.requireNonNull( url );
        McpHttpEndpoint endpoint = McpHttpEndpoint.parse( url );
        Map<String, String> safeHeaders = headers != null ? headers : Collections.emptyMap();

        var transportBuilder = HttpClientStreamableHttpTransport.builder( endpoint.baseUrl() )
                .endpoint( endpoint.endpointPath() );
        if ( !safeHeaders.isEmpty() )
        {
            transportBuilder.httpRequestCustomizer( ( requestBuilder, method, uri, body, context ) -> {
                for ( Map.Entry<String, String> header : safeHeaders.entrySet() )
                {
                    requestBuilder.header( header.getKey(), header.getValue() );
                }
            } );
        }

        return McpClient.sync( transportBuilder.build() )
                .requestTimeout( REQUEST_TIMEOUT )
                .jsonSchemaValidator( new JacksonJsonSchemaValidatorSupplier().get() )
                .build();
    }

    /**
     * Connects to the given HTTP MCP endpoint, lists tools, and closes the client.
     */
    public static List<String> discoverToolNames( String url, Map<String, String> headers )
    {
        McpSyncClient client = createHttpClient( url, headers );
        try
        {
            client.initialize();
            McpSchema.ListToolsResult toolsResult = client.listTools();
            if ( toolsResult == null || toolsResult.tools() == null )
            {
                return Collections.emptyList();
            }
            List<String> names = new ArrayList<>();
            for ( McpSchema.Tool tool : toolsResult.tools() )
            {
                names.add( tool.name() );
            }
            Collections.sort( names );
            return names;
        }
        finally
        {
            client.closeGracefully();
        }
    }
}
