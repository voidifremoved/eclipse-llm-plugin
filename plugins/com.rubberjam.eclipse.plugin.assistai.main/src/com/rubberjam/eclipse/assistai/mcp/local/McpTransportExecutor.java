package com.rubberjam.eclipse.assistai.mcp.local;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Per transport-pair threading: blocking queue readers run on dedicated daemon threads;
 * Reactor {@code subscribeOn}/{@code publishOn} use a separate pool so initialize/handshake
 * cannot starve when two I/O loops are parked on {@code BlockingQueue.take()}.
 */
final class McpTransportExecutor
{
    private static final AtomicInteger TRANSPORT_COUNTER = new AtomicInteger();

    private final int transportId;

    private final ExecutorService reactorExecutor;

    private final AtomicInteger ioThreadCounter = new AtomicInteger();

    private final List<Thread> ioThreads = new ArrayList<>();

    private volatile boolean shutdown;

    McpTransportExecutor()
    {
        transportId = TRANSPORT_COUNTER.incrementAndGet();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread( runnable, "assistai-mcp-reactor-" + transportId );
            thread.setDaemon( true );
            return thread;
        };
        this.reactorExecutor = Executors.newFixedThreadPool( 4, factory );
    }

    Scheduler getScheduler()
    {
        return Schedulers.fromExecutor( reactorExecutor );
    }

    /**
     * Long-lived {@code BlockingQueue.take()} loop; must not use the reactor thread pool.
     */
    void startBlockingIoLoop( Runnable loop )
    {
        Thread thread;
        synchronized ( ioThreads )
        {
            if ( shutdown )
            {
                return;
            }
            int n = ioThreadCounter.incrementAndGet();
            thread = new Thread( loop, "assistai-mcp-io-" + transportId + "-" + n );
            thread.setDaemon( true );
            ioThreads.add( thread );
        }
        thread.start();
    }

    void shutdown()
    {
        if ( shutdown )
        {
            return;
        }
        synchronized ( ioThreads )
        {
            if ( shutdown )
            {
                return;
            }
            shutdown = true;
            for ( Thread thread : ioThreads )
            {
                thread.interrupt();
            }
            ioThreads.clear();
        }
        reactorExecutor.shutdownNow();
    }
}
