package com.rubberjam.eclipse.assistai;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;

public class PluginStartup implements IStartup
{
    @Override
    public void earlyStartup()
    {
        // IStartup runs after the workbench is initialized; an active window is unnecessary.
        var workbench = PlatformUI.getWorkbench();
        var display = workbench.getDisplay();
        display.asyncExec( () -> {
            var plugin = Activator.getDefault();
            if ( !display.isDisposed() && plugin != null && !workbench.isClosing() )
            {
                plugin.startMcpServices();
            }
        } );
    }
}
