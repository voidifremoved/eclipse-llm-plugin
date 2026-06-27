package com.rubberjam.eclipse.assistai.mcp;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.mcp.annotations.Tool;
import com.rubberjam.eclipse.assistai.mcp.annotations.ToolParam;
import com.rubberjam.eclipse.assistai.tools.UISynchronizeCallable;

/**
 * Invokes {@link Tool}-annotated methods. Eclipse workspace/JDT work must run on the SWT UI thread.
 */
public class ToolExecutor
{
    private static final AtomicInteger TOOL_THREAD_COUNTER = new AtomicInteger();

    private static final ExecutorService TOOL_EXECUTOR = Executors.newCachedThreadPool( new ThreadFactory()
    {
        @Override
        public Thread newThread( Runnable runnable )
        {
            Thread thread = new Thread( runnable, "assistai-mcp-tool-" + TOOL_THREAD_COUNTER.incrementAndGet() );
            thread.setDaemon( true );
            return thread;
        }
    } );

    private final Object functions;

    private final UISynchronizeCallable uiSync;

    public ToolExecutor( Object functions )
    {
        this( functions, null );
    }

    public ToolExecutor( Object functions, UISynchronizeCallable uiSync )
    {
        this.functions = functions;
        this.uiSync = uiSync != null ? uiSync : Activator.getDefault().make( UISynchronizeCallable.class );
    }

    /**
     * Retrieves an array of {@link Method}s that are declared as a function_call
     * callback with the {@link Tool} annotation.
     *
     * @return
     */
    public Method[] getFunctions()
    {
        return Arrays.stream( functions.getClass().getDeclaredMethods() )
                .filter( method -> Objects.nonNull( method.getAnnotation( com.rubberjam.eclipse.assistai.mcp.annotations.Tool.class ) ) )
                .toArray( Method[]::new );
    }



    public CompletableFuture<Object> call( String name, Map<String, Object> args )
    {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return CompletableFuture.supplyAsync( () -> {
            Thread thread = Thread.currentThread();
            ClassLoader oldClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader( contextClassLoader );
            try
            {
                Method method = getFunctionCallbackByName( name ).orElseThrow( () -> new RuntimeException( "Tool " + name + " not found!" ) );
                Object[] argValues = mapArguments( method, args );
                return uiSync.syncCall( () -> invokeMethod( method, argValues ) );
            }
            finally
            {
                thread.setContextClassLoader( oldClassLoader );
            }
        }, TOOL_EXECUTOR );
    }

    private Object invokeMethod( Method method, Object[] args )
    {
        try
        {
            return method.invoke( functions, args );
        }
        catch ( InvocationTargetException e )
        {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if ( cause instanceof RuntimeException runtime )
            {
                throw runtime;
            }
            throw new RuntimeException( cause );
        }
        catch ( IllegalAccessException | IllegalArgumentException e )
        {
            throw new RuntimeException( e );
        }
    }

    public CompletableFuture<Object> call( String name, String[] args )
    {
        return call( name, toMap(args) );
    }

    /**
     * Creates an array of parameter values as declared by the callback {@link Method}
     *
     * @param method
     * @param argMap
     * @return
     */
    public Object[] mapArguments( Method method, Map<String, Object> argMap )
    {
        Parameter[] parameters = method.getParameters();
        Object[] values = new Object[parameters.length];
        for ( int i = 0; i < parameters.length; i++ )
        {
            Parameter parameter = parameters[i];
            String name = toParamName( parameter );
            values[i] = coerceArgument( argMap.get( name ), parameter.getType() );
        }
        return values;
    }

    /**
     * Coerces JSON-deserialized tool arguments to the declared Java parameter type.
     * Models often send booleans and numbers as native JSON types while tools declare String parameters.
     */
    static Object coerceArgument( Object raw, Class<?> targetType )
    {
        if ( targetType == null )
        {
            return raw;
        }
        if ( raw == null )
        {
            if ( targetType == boolean.class )
            {
                return false;
            }
            if ( targetType == int.class )
            {
                return 0;
            }
            if ( targetType == long.class )
            {
                return 0L;
            }
            if ( targetType == double.class )
            {
                return 0.0d;
            }
            if ( targetType == float.class )
            {
                return 0.0f;
            }
            return null;
        }
        if ( targetType.isInstance( raw ) )
        {
            return raw;
        }
        if ( targetType == String.class )
        {
            return raw instanceof String text ? text : String.valueOf( raw );
        }
        if ( targetType == boolean.class || targetType == Boolean.class )
        {
            if ( raw instanceof Boolean bool )
            {
                return bool;
            }
            return Boolean.parseBoolean( String.valueOf( raw ) );
        }
        if ( targetType == int.class || targetType == Integer.class )
        {
            if ( raw instanceof Number number )
            {
                return number.intValue();
            }
            return Integer.parseInt( String.valueOf( raw ) );
        }
        if ( targetType == long.class || targetType == Long.class )
        {
            if ( raw instanceof Number number )
            {
                return number.longValue();
            }
            return Long.parseLong( String.valueOf( raw ) );
        }
        if ( targetType == double.class || targetType == Double.class )
        {
            if ( raw instanceof Number number )
            {
                return number.doubleValue();
            }
            return Double.parseDouble( String.valueOf( raw ) );
        }
        if ( targetType == float.class || targetType == Float.class )
        {
            if ( raw instanceof Number number )
            {
                return number.floatValue();
            }
            return Float.parseFloat( String.valueOf( raw ) );
        }
        return raw;
    }

    /**
     * Converts a String array of key-value pairs into a Map.
     *
     * @param keyVal the String array of key-value pairs
     * @return the Map representation of the key-value pairs
     * @throws IllegalArgumentException if the input array is not a key-value array
     */
    public Map<String, Object> toMap( String[] keyVal )
    {
        if ( keyVal.length % 2 != 0 )
        {
            throw new IllegalArgumentException("Not a key-val array");
        }
        var map = new HashMap<String, Object>();
        for (int i = 0; i < keyVal.length; i += 2)
        {
            map.put(keyVal[i], keyVal[i + 1]);
        }
        return map;
    }

    /**
     * Retrieves the function callback method with the specified name.
     *
     * @param name the name of the function
     * @return an Optional containing the function callback method, or an empty Optional if the function is not found
     */
    public Optional<Method> getFunctionCallbackByName( String name )
    {
        return Arrays.stream( getFunctions() )
                     .filter( method -> toFunctionName( method ).equals( name ) )
                     .findFirst();
    }
    /**
     * Converts a Parameter object to its corresponding parameter name.
     *
     * @param parameter the Parameter object
     * @return the parameter name, or the annotated name if present, or the default name if no annotation is found
     */
    public static String toParamName( Parameter parameter )
    {
        return Optional.ofNullable( parameter.getAnnotation( com.rubberjam.eclipse.assistai.mcp.annotations.ToolParam.class ) )
                    .map( ToolParam::name )
                    .filter( Predicate.not( String::isBlank ) )
                    .orElse( parameter.getName() );
    }
    /**
     * Retrieves the name of the function based on the provided Method object.
     *
     * @param method the Method object representing the function
     * @return the name of the function, or the annotated name if present, or the default name if no annotation is found
     */
    public static String toFunctionName( Method method )
    {
        return Optional.ofNullable( method.getAnnotation( com.rubberjam.eclipse.assistai.mcp.annotations.Tool.class ) )
                .map( Tool::name )
                .filter( Predicate.not(String::isBlank))
                .orElse( method.getName() );
    }

}
