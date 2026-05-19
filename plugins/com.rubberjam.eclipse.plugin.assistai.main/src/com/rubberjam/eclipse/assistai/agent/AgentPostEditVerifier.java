package com.rubberjam.eclipse.assistai.agent;

import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jface.preference.IPreferenceStore;

import com.rubberjam.eclipse.assistai.Activator;
import com.rubberjam.eclipse.assistai.preferences.PreferenceConstants;
import com.rubberjam.eclipse.assistai.prompt.PromptContextValueProvider;
import com.rubberjam.eclipse.assistai.springai.BuiltinMcpToolRouter;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Optional compile verification after eclipse-coder edits (Phase 2.4).
 */
@Creatable
@Singleton
public final class AgentPostEditVerifier
{
    private static final String VERIFY_TOOL = "eclipse-ide__getCompilationErrors";

    @Inject
    private BuiltinMcpToolRouter builtinToolRouter;

    @Inject
    private PromptContextValueProvider contextValues;

    @Inject
    private AgentCompilationErrorScope compilationErrorScope;

    public boolean isVerifyAfterEditEnabled()
    {
        return getPreferenceStore().getBoolean( PreferenceConstants.ASSISTAI_AGENT_VERIFY_AFTER_EDIT );
    }

    public boolean isCoderTool( String toolName )
    {
        if ( toolName == null )
        {
            return false;
        }
        return toolName.startsWith( "eclipse-coder__" );
    }

    /**
     * @return verification footer to append to tool result, or empty if disabled / failed
     */
    public String verifyAfterEditFooter()
    {
        if ( !isVerifyAfterEditEnabled() || !builtinToolRouter.isRegistered( VERIFY_TOOL ) )
        {
            return "";
        }
        String project = contextValues.getContextValue( "currentProjectName" );
        if ( project == null || project.isBlank() )
        {
            return "\n\n---\n**Compile check:** (no active project)\n";
        }
        String filePath = null;
        if ( compilationErrorScope != null && compilationErrorScope.isActive() )
        {
            filePath = compilationErrorScope.get().filePath();
        }
        if ( filePath == null || filePath.isBlank() )
        {
            filePath = contextValues.getContextValue( "currentFilePath" );
        }
        StringBuilder argsJson = new StringBuilder();
        argsJson.append( "{ \"projectName\": \"" ).append( escapeJson( project ) ).append( "\"" );
        argsJson.append( ", \"severity\": \"ERROR\", \"maxResults\": 30" );
        if ( filePath != null && !filePath.isBlank() )
        {
            argsJson.append( ", \"filePath\": \"" ).append( escapeJson( filePath ) ).append( "\"" );
        }
        argsJson.append( " }" );
        String args = argsJson.toString();
        return builtinToolRouter.tryInvoke( VERIFY_TOOL, args )
                .map( result -> "\n\n---\n**Compile check (auto):**\n```\n" + result + "\n```\n" )
                .orElse( "" );
    }

    private static String escapeJson( String value )
    {
        return value.replace( "\\", "\\\\" ).replace( "\"", "\\\"" );
    }

    private IPreferenceStore getPreferenceStore()
    {
        return Activator.getDefault().getPreferenceStore();
    }
}
