package com.rubberjam.eclipse.assistai;

import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import io.micrometer.context.ContextAccessor;
import io.micrometer.context.ContextRegistry;
import reactor.util.context.ReactorContextAccessor;

import com.rubberjam.eclipse.assistai.models.ModelApiDescriptorRepository;
import com.rubberjam.eclipse.assistai.preferences.mcp.McpHttpServerPreferencePresenter;
import com.rubberjam.eclipse.assistai.preferences.mcp.McpServerPreferencePresenter;
import com.rubberjam.eclipse.assistai.preferences.models.ModelListPreferencePresenter;
import com.rubberjam.eclipse.assistai.preferences.prompts.PromptsPreferencePresenter;

public class Activator extends AbstractUIPlugin
{
    private static Activator plugin = null;

    @Override
    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        plugin = this;
        registerReactorContextAccessor();
    }

    /**
     * OSGi does not reliably load {@link ReactorContextAccessor} via {@code ServiceLoader},
     * but Spring AI / Micrometer enable Reactor context propagation which requires it.
     */
    private void registerReactorContextAccessor()
    {
        ContextRegistry registry = ContextRegistry.getInstance();
        for (ContextAccessor accessor : registry.getContextAccessors())
        {
            if (accessor instanceof ReactorContextAccessor)
            {
                return;
            }
        }
        registry.registerContextAccessor(new ReactorContextAccessor());
    }

    public static Activator getDefault()
    {
        return plugin;
    }

    public PromptsPreferencePresenter getPromptsPreferencePresenter()
    {
        return  make ( PromptsPreferencePresenter.class );
    }

    public ModelListPreferencePresenter getModelsPreferencePresenter()
    {
        return make ( ModelListPreferencePresenter.class );

    }

    public McpServerPreferencePresenter getMCPServerPreferencePresenter()
    {
        return make( McpServerPreferencePresenter.class );
    }

    public McpHttpServerPreferencePresenter getHttpMcpServerPreferencePresenter()
    {
        return make( McpHttpServerPreferencePresenter.class );
    }

    public ModelApiDescriptorRepository getModelApiDescriptorRepository()
    {
        return make( ModelApiDescriptorRepository.class );
    }

    public <T> T make ( Class<T> clazz )
    {
        return ContextInjectionFactory.make( clazz, resolveEclipseContext() );
    }

    private IEclipseContext resolveEclipseContext()
    {
        try
        {
            if ( PlatformUI.isWorkbenchRunning() )
            {
                IWorkbench workbench = PlatformUI.getWorkbench();
                IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
                if ( window != null )
                {
                    IEclipseContext windowContext = window.getService( IEclipseContext.class );
                    if ( windowContext != null )
                    {
                        return windowContext;
                    }
                }
                IEclipseContext workbenchContext = workbench.getService( IEclipseContext.class );
                if ( workbenchContext != null )
                {
                    return workbenchContext;
                }
            }
        }
        catch ( Exception ignored )
        {
            // fall through
        }
        BundleContext bundleContext = getBundle().getBundleContext();
        return EclipseContextFactory.getServiceContext( bundleContext );
    }
}
