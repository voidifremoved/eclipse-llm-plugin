package com.rubberjam.eclipse.assistai;

import org.eclipse.core.runtime.ILog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.rubberjam.eclipse.assistai.mcp.http.HttpMcpServerRegistry;
import com.rubberjam.eclipse.assistai.mcp.local.InMemoryMcpClientRetistry;

public class PluginStartup implements IStartup
{
    @Override
    public void earlyStartup()
    {
        Display.getDefault().asyncExec(() -> {
            waitForWorkbench();
        });
    }
    
    private void waitForWorkbench() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window != null) {
                ILog logger = Activator.getDefault().getLog();
                logger.info("Initializing MCP services on UI thread");
                Activator.getDefault().make(HttpMcpServerRegistry.class);
                Activator.getDefault().make(InMemoryMcpClientRetistry.class).ensureInitialized();
            } else {
                // Window exists but not active yet, retry
                Display.getDefault().timerExec(500, this::waitForWorkbench);
            }
        } catch (IllegalStateException e) {
            // Workbench not created yet, retry
            Display.getDefault().timerExec(500, this::waitForWorkbench);
        }
    }
}
