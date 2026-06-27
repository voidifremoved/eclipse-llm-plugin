package com.rubberjam.eclipse.assistai.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.agent.AgentViewPresenter;

public class AssistAIAgentStopHandler extends AbstractHandler
{
    @Override
    public Object execute( ExecutionEvent event ) throws ExecutionException
    {
        Activator.getDefault().make( AgentViewPresenter.class ).onStop();
        return null;
    }
}
