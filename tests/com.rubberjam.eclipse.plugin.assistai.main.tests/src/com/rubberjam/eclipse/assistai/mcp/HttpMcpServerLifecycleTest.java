package com.rubberjam.eclipse.assistai.mcp;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.eclipse.core.runtime.ILog;
import org.junit.jupiter.api.Test;
import com.rubberjam.eclipse.assistai.mcp.annotations.McpServer;
import com.rubberjam.eclipse.assistai.mcp.annotations.Tool;
import com.rubberjam.eclipse.assistai.mcp.http.*;
import com.rubberjam.eclipse.assistai.tools.UISynchronizeCallable;

public class HttpMcpServerLifecycleTest
{
    @McpServer(name = "probe")
    public static class Probe
    {
        @Tool(description = "Liveness probe", uiThread = false)
        public String ping() { return "pong"; }
    }

    private HttpMcpServerRegistry registry( int port )
    {
        var logger = ILog.of( getClass() );
        var preferences = new HttpMcpServerPreferencesProvider() {
            @Override public HttpMcpServerPreferences get() {
                return new HttpMcpServerPreferences( port, "127.0.0.1", "test-token" );
            }
            @Override public boolean isEnabled() { return true; }
        };
        var repository = new McpServerRepository( new McpServerBuiltins(), logger ) {
            @Override public List<McpServerDescriptor> listBuiltInServers() {
                return List.of( new McpServerDescriptor( "probe", "probe", "", List.of(), true, true, List.of(), "" ) );
            }
            @Override public List<McpServerDescriptor> listStoredServers() { return listBuiltInServers(); }
            @Override public Object makeImplementation( String name ) { return new Probe(); }
        };
        return new HttpMcpServerRegistry( preferences, repository,
                new McpServerFactory( logger, new UISynchronizeCallable() ), logger );
    }

    @Test public void bindFailureRecoversAndShutdownReleasesPort() throws Exception
    {
        var occupied = new ServerSocket( 0, 50, InetAddress.getByName( "127.0.0.1" ) );
        int port = occupied.getLocalPort();
        var registry = registry( port );
        try
        {
            registry.restart();
            assertFalse( registry.isRunning() );
            assertNotNull( registry.getLastFailure() );
            assertTrue( registry.listEndpoints().isEmpty() );
            occupied.close();
            long deadline = System.nanoTime() + Duration.ofSeconds( 15 ).toNanos();
            while ( !registry.isRunning() && System.nanoTime() < deadline ) Thread.sleep( 50 );
            assertTrue( registry.isRunning(), registry.getLastFailure() );
            assertNull( registry.getLastFailure() );
            try ( var client = HttpClient.newHttpClient() )
            {
                var request = HttpRequest.newBuilder( URI.create( registry.listEndpoints().getFirst() ) )
                        .timeout( Duration.ofSeconds( 5 ) )
                        .header( "Content-Type", "application/json" )
                        .header( "Accept", "application/json, text/event-stream" )
                        .POST( HttpRequest.BodyPublishers.ofString(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}" ) );
                assertEquals( 401, client.send( request.build(), HttpResponse.BodyHandlers.ofString() ).statusCode() );
                var response = client.send( request.header( "Authorization", "Bearer test-token" ).build(),
                        HttpResponse.BodyHandlers.ofString() );
                assertEquals( 200, response.statusCode(), response.body() );
                assertTrue( response.body().contains( "serverInfo" ), response.body() );
                request.header( "Mcp-Session-Id", response.headers().firstValue( "Mcp-Session-Id" ).orElseThrow() );
                request.POST( HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}" ) );
                assertEquals( 202, client.send( request.build(), HttpResponse.BodyHandlers.ofString() ).statusCode() );
                request.POST( HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}" ) );
                var listed = client.send( request.build(), HttpResponse.BodyHandlers.ofString() );
                assertEquals( 200, listed.statusCode() );
                assertTrue( listed.body().contains( "ping" ), listed.body() );
                request.POST( HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"ping\"}}" ) );
                var called = client.send( request.build(), HttpResponse.BodyHandlers.ofString() );
                assertEquals( 200, called.statusCode() );
                assertTrue( called.body().contains( "pong" ), called.body() );
            }
            registry.restart();
            assertTrue( registry.isRunning(), registry.getLastFailure() );
        }
        finally
        {
            occupied.close();
            registry.handleShutdown();
        }
        assertFalse( registry.isRunning() );
        try ( var released = new ServerSocket( port, 50, InetAddress.getByName( "127.0.0.1" ) ) )
        {
            assertEquals( port, released.getLocalPort() );
        }
        registry.restart();
        assertFalse( registry.isRunning(), "Shutdown must prevent scheduled resurrection" );
    }
}
