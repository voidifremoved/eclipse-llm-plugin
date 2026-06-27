package com.rubberjam.eclipse.assistai.springai;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubberjam.eclipse.assistai.agent.AgentCompilationErrorScope;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.mcp.McpServerRepository;
import com.rubberjam.eclipse.assistai.mcp.ToolExecutor;
import com.rubberjam.eclipse.assistai.mcp.annotations.Tool;
import com.rubberjam.eclipse.assistai.mcp.annotations.ToolParam;
import com.rubberjam.eclipse.assistai.tools.UISynchronizeCallable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

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

    private final AgentCompilationErrorScope compilationErrorScope;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Map<String, BuiltinToolRegistration> toolsByName;

    @Inject
    public BuiltinMcpToolRouter(
            McpServerRepository serverRepository,
            UISynchronizeCallable uiSync,
            AgentCompilationErrorScope compilationErrorScope )
    {
        this.serverRepository = serverRepository;
        this.uiSync = uiSync;
        this.compilationErrorScope = compilationErrorScope;
    }

    public void clearCache()
    {
        toolsByName = null;
    }

    /**
     * @return tool result text when this is a registered built-in tool, otherwise empty
     */
    public boolean isRegistered( String toolName )
    {
        if ( toolName == null || toolName.isBlank() )
        {
            return false;
        }
        ensureLoaded();
        return toolsByName.containsKey( toolName );
    }

    public Optional<ToolDefinition> getToolDefinition( String toolName )
    {
        if ( toolName == null || toolName.isBlank() )
        {
            return Optional.empty();
        }
        ensureLoaded();
        BuiltinToolRegistration registration = toolsByName.get( toolName );
        if ( registration == null )
        {
            return Optional.empty();
        }
        return Optional.of( registration.definition() );
    }

    public Optional<String> tryInvoke( String toolName, String toolInputJson )
    {
        Objects.requireNonNull( toolName, "toolName" );
        ensureLoaded();
        BuiltinToolRegistration registration = toolsByName.get( toolName );
        if ( registration == null )
        {
            return Optional.empty();
        }
        Map<String, Object> args = parseToolInput( toolInputJson );
        String bareToolName = AssistAiMcpToolNames.bareToolName( toolName );
        if ( "getCompilationErrors".equals( bareToolName ) )
        {
            args = applyCompilationErrorScope( args );
        }
        Object result = registration.executor().call( bareToolName, args ).join();
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
            Map<String, BuiltinToolRegistration> map = new HashMap<>();
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
                        map.put( name, new BuiltinToolRegistration(
                                executor,
                                createToolDefinition( name, method ) ) );
                        String prefixedName = AssistAiMcpToolNames.prefixed( descriptor.name(), name );
                        map.put( prefixedName, new BuiltinToolRegistration(
                                executor,
                                createToolDefinition( prefixedName, method ) ) );
                    }
                }
            }
            toolsByName = Map.copyOf( map );
        }
    }

    private ToolDefinition createToolDefinition( String registeredName, Method method )
    {
        Tool toolAnnotation = method.getAnnotation( Tool.class );
        String description = toolAnnotation != null ? toolAnnotation.description() : registeredName;
        String schema = createInputSchemaJson( method, toolAnnotation );
        return DefaultToolDefinition.builder()
                .name( registeredName )
                .description( description )
                .inputSchema( schema )
                .build();
    }

    private String createInputSchemaJson( Method method, Tool toolAnnotation )
    {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for ( Parameter parameter : method.getParameters() )
        {
            ToolParam toolParam = parameter.getAnnotation( ToolParam.class );
            if ( toolParam == null )
            {
                continue;
            }
            String name = ToolExecutor.toParamName( parameter );
            Map<String, Object> property = new LinkedHashMap<>();
            property.put( "type", toolParam.type() );
            property.put( "description", toolParam.description() );
            properties.put( name, property );
            if ( toolParam.required() )
            {
                required.add( name );
            }
        }
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put( "type", toolAnnotation != null ? toolAnnotation.type() : "object" );
        inputSchema.put( "properties", properties );
        if ( !required.isEmpty() )
        {
            inputSchema.put( "required", required );
        }
        try
        {
            return objectMapper.writeValueAsString( inputSchema );
        }
        catch ( Exception e )
        {
            return "{\"type\":\"object\",\"properties\":{}}";
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

    private Map<String, Object> applyCompilationErrorScope( Map<String, Object> args )
    {
        if ( compilationErrorScope == null || !compilationErrorScope.isActive() )
        {
            return args;
        }
        AgentCompilationErrorScope.Scope scope = compilationErrorScope.get();
        Map<String, Object> merged = new HashMap<>( args );
        Object existingFile = merged.get( "filePath" );
        if ( existingFile == null || String.valueOf( existingFile ).isBlank() )
        {
            merged.put( "filePath", scope.filePath() );
        }
        Object existingProject = merged.get( "projectName" );
        if ( ( existingProject == null || String.valueOf( existingProject ).isBlank() )
                && scope.projectName() != null && !scope.projectName().isBlank() )
        {
            merged.put( "projectName", scope.projectName() );
        }
        return merged;
    }

    private record BuiltinToolRegistration( ToolExecutor executor, ToolDefinition definition )
    {
    }
}
