package com.rubberjam.eclipse.assistai.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.agent.AgentViewPresenter;

public class AssistAIAgentNewTabHandler extends AbstractHandler
{
    @Override
    public Object execute( ExecutionEvent event ) throws ExecutionException
    {
        AgentViewPresenter presenter = Activator.getDefault().make( AgentViewPresenter.class );
        presenter.focusAgentView();
        presenter.onNewAgentTab();
        return null;
    }
}
