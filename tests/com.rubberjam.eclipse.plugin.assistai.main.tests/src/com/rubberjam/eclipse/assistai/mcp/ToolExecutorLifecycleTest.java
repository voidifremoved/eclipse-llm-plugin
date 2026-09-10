package com.rubberjam.eclipse.assistai.mcp;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import com.rubberjam.eclipse.assistai.mcp.annotations.Tool;
import com.rubberjam.eclipse.assistai.tools.UISynchronizeCallable;

public class ToolExecutorLifecycleTest
{
    static class QueuedUi extends UISynchronizeCallable
    {
        Runnable pending;
        @Override public void asyncExec( Runnable runnable ) { pending = runnable; }
    }

    public static class Probe
    {
        int edits;
        @Tool(description = "edit") public String edit() { edits++; return "edited"; }
        @Tool(description = "status", uiThread = false) public String status() { return "ready"; }
    }

    @Test public void cancelledQueuedEditDoesNotExecute()
    {
        var probe = new Probe();
        var ui = new QueuedUi();
        var executor = new ToolExecutor( probe, ui );
        var result = executor.call( "edit", (Map<String, Object>) null );
        assertFalse( result.isDone() );
        result.cancel( true );
        ui.pending.run();
        assertEquals( 0, probe.edits );
    }

    @Test public void backgroundToolDoesNotWaitForUi() throws Exception
    {
        var ui = new QueuedUi();
        var executor = new ToolExecutor( new Probe(), ui );
        assertEquals( "ready", executor.call( "status", Map.of() ).get( 2, TimeUnit.SECONDS ) );
        assertNull( ui.pending );
    }

    @Test public void unnamedToolUsesMethodNameAndAcceptsOmittedArguments() throws Exception
    {
        var ui = new QueuedUi();
        var executor = new ToolExecutor( new Probe(), ui );
        var result = executor.call( "edit", (Map<String, Object>) null );
        ui.pending.run();
        assertEquals( "edited", result.get() );
    }
}
