package com.rubberjam.eclipse.assistai.springai;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.e4.core.di.annotations.Creatable;

import jakarta.inject.Singleton;

/**
 * Runs blocking MCP {@code callTool()} invocations off Reactor's bounded-elastic pool so in-memory
 * transport threads can still deliver responses.
 */
@Creatable
@Singleton
public final class McpToolInvocationExecutor
{
    private static final long TOOL_TIMEOUT_MINUTES = 5;

    private final ExecutorService executor = Executors.newCachedThreadPool( new ThreadFactory()
    {
        private int counter;

        @Override
        public Thread newThread( Runnable runnable )
        {
            Thread thread = new Thread( runnable, "assistai-mcp-tool-" + ++counter );
            thread.setDaemon( true );
            return thread;
        }
    } );

    public String invoke( Callable<String> callable ) throws ExecutionException, InterruptedException, TimeoutException
    {
        return executor.submit( callable ).get( TOOL_TIMEOUT_MINUTES, TimeUnit.MINUTES );
    }

    void shutdown()
    {
        executor.shutdownNow();
    }
}
