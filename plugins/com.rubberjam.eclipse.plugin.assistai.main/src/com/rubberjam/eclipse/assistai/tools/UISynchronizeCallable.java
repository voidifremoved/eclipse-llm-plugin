package com.rubberjam.eclipse.assistai.tools;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.PlatformUI;

/**
 * Runs callables on the SWT UI thread. Resolves {@link UISynchronize} lazily because
 * {@code @Inject UISynchronize} fails when services are created via {@code Activator.make()}
 * before the E4 application context exposes that service.
 */
@Creatable
public class UISynchronizeCallable
{
    private volatile UISynchronize uiSync;

    private UISynchronize getE4UiSync()
    {
        UISynchronize local = uiSync;
        if ( local == null )
        {
            synchronized ( this )
            {
                local = uiSync;
                if ( local == null )
                {
                    local = lookupE4UiSynchronize();
                    uiSync = local;
                }
            }
        }
        return local;
    }

    private static UISynchronize lookupE4UiSynchronize()
    {
        try
        {
            if ( PlatformUI.isWorkbenchRunning() )
            {
                IEclipseContext context = PlatformUI.getWorkbench().getService( IEclipseContext.class );
                if ( context != null )
                {
                    return context.get( UISynchronize.class );
                }
            }
        }
        catch ( Exception ignored )
        {
            // use Display fallback
        }
        return null;
    }

    public void syncExec( Runnable runnable )
    {
        UISynchronize sync = getE4UiSync();
        if ( sync != null )
        {
            sync.syncExec( runnable );
            return;
        }
        Display display = Display.getDefault();
        if ( display == null || display.isDisposed() )
        {
            return;
        }
        if ( Display.getCurrent() == display )
        {
            runnable.run();
        }
        else
        {
            display.syncExec( runnable );
        }
    }

    public void asyncExec( Runnable runnable )
    {
        UISynchronize sync = getE4UiSync();
        if ( sync != null )
        {
            sync.asyncExec( runnable );
            return;
        }
        Display display = Display.getDefault();
        if ( display != null && !display.isDisposed() )
        {
            display.asyncExec( runnable );
        }
    }

    /**
     * Runs on the UI thread only if {@code guard} is still valid (not null, not disposed).
     */
    public void asyncExecIfAlive( Widget guard, Runnable runnable )
    {
        asyncExec( () -> {
            if ( WidgetGuards.isAlive( guard ) )
            {
                runnable.run();
            }
        } );
    }

    public <T> Future<T> asyncCall( Callable<T> callable )
    {
        CompletableFuture<T> future = new CompletableFuture<>();
        asyncExec( () -> {
            try
            {
                future.complete( callable.call() );
            }
            catch ( Exception e )
            {
                future.completeExceptionally( e );
            }
        } );
        return future;
    }

    /**
     * Executes a task in the UI thread synchronously.
     */
    public <T> T syncCall( Callable<T> callable )
    {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> exception = new AtomicReference<>();

        syncExec( () -> {
            try
            {
                result.set( callable.call() );
            }
            catch ( Exception e )
            {
                exception.set( e );
            }
        } );
        if ( Objects.nonNull( exception.get() ) )
        {
            Exception e = exception.get();
            throw new RuntimeException( e.getMessage(), e );
        }
        return result.get();
    }
}
