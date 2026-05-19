package com.rubberjam.eclipse.assistai.springai;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;
import com.rubberjam.eclipse.assistai.mcp.ToolExecutor;
import com.rubberjam.eclipse.assistai.tools.UISynchronizeCallable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Invokes built-in Eclipse MCP tools in-process (no JSON-RPC round trip). Used by the agent UI to
 * avoid in-memory transport deadlocks.
 */
@Creatable
@Singleton
public final class BuiltinMcpToolRouter
{
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
    {
    };

    private final McpServerRepository serverRepository;

    private final UISynchronizeCallable uiSync;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Map<String, ToolExecutor> toolsByName;

    @Inject
    public BuiltinMcpToolRouter( McpServerRepository serverRepository, UISynchronizeCallable uiSync )
    {
        this.serverRepository = serverRepository;
        this.uiSync = uiSync;
    }

    public void clearCache()
    {
        toolsByName = null;
    }

    /**
     * @return tool result text when this is a registered built-in tool, otherwise empty
     */
    public Optional<String> tryInvoke( String toolName, String toolInputJson )
    {
        Objects.requireNonNull( toolName, "toolName" );
        ensureLoaded();
        ToolExecutor executor = toolsByName.get( toolName );
        if ( executor == null )
        {
            return Optional.empty();
        }
        Map<String, Object> args = parseToolInput( toolInputJson );
        Object result = executor.call( toolName, args ).join();
        if ( result == null )
        {
            return Optional.of( "" );
        }
        if ( result instanceof String text )
        {
            return Optional.of( text );
        }
        try
        {
            return Optional.of( objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString( result ) );
        }
        catch ( Exception e )
        {
            return Optional.of( result.toString() );
        }
    }

    private void ensureLoaded()
    {
        if ( toolsByName != null )
        {
            return;
        }
        synchronized ( this )
        {
            if ( toolsByName != null )
            {
                return;
            }
            Map<String, ToolExecutor> map = new HashMap<>();
            for ( McpServerDescriptor descriptor : serverRepository.listStoredServers() )
            {
                if ( !descriptor.builtIn() || !descriptor.enabled() )
                {
                    continue;
                }
                Object implementation = serverRepository.makeImplementation( descriptor.name() );
                ToolExecutor executor = new ToolExecutor( implementation, uiSync );
                for ( Method method : executor.getFunctions() )
                {
                    String name = ToolExecutor.toFunctionName( method );
                    if ( !descriptor.excludedTools().contains( name ) )
                    {
                        map.put( name, executor );
                        map.put( descriptor.name() + "__" + name, executor );
                    }
                }
            }
            toolsByName = Map.copyOf( map );
        }
    }

    private Map<String, Object> parseToolInput( String toolInputJson )
    {
        if ( toolInputJson == null || toolInputJson.isBlank() )
        {
            return Collections.emptyMap();
        }
        try
        {
            return objectMapper.readValue( toolInputJson, MAP_TYPE );
        }
        catch ( Exception e )
        {
            return Collections.emptyMap();
        }
    }
}
