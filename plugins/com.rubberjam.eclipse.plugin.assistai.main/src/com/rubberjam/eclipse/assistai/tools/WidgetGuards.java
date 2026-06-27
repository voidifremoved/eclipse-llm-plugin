package com.rubberjam.eclipse.assistai.tools;

import org.eclipse.swt.widgets.Widget;

/**
 * Helpers for safe SWT updates from background threads.
 */
public final class WidgetGuards
{
    private WidgetGuards()
    {
    }

    public static boolean isAlive( Widget widget )
    {
        return widget != null && !widget.isDisposed();
    }
}
