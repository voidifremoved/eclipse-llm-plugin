package com.rubberjam.eclipse.assistai.agent;

import org.eclipse.e4.core.di.annotations.Creatable;

import jakarta.inject.Singleton;

/**
 * Per-request scope for compilation-error tools: limits {@code getCompilationErrors}
 * and {@code executeQuickFix} to the active editor file when the user asked to fix
 * "this file" or used the Fix Errors command.
 */
@Creatable
@Singleton
public final class AgentCompilationErrorScope
{
    public record Scope( String projectName, String filePath )
    {
    }

    private final ThreadLocal<Scope> current = new ThreadLocal<>();

    public void set( Scope scope )
    {
        if ( scope == null || scope.filePath() == null || scope.filePath().isBlank() )
        {
            clear();
            return;
        }
        current.set( scope );
    }

    public Scope get()
    {
        return current.get();
    }

    public void clear()
    {
        current.remove();
    }

    public boolean isActive()
    {
        Scope scope = get();
        return scope != null && scope.filePath() != null && !scope.filePath().isBlank();
    }
}
