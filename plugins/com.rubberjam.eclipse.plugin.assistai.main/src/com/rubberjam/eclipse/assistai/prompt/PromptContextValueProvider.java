package com.rubberjam.eclipse.assistai.prompt;

import java.util.Objects;
import java.util.concurrent.Callable;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.mcp.services.CodeAnalysisService;
import com.rubberjam.eclipse.assistai.mcp.services.ConsoleService;
import com.rubberjam.eclipse.assistai.mcp.services.EditorService;
import com.rubberjam.eclipse.assistai.mcp.services.GitService;
import com.rubberjam.eclipse.assistai.resources.ResourceCache;
import com.rubberjam.eclipse.assistai.resources.ResourceToolResult;
import com.rubberjam.eclipse.assistai.tools.UISynchronizeCallable;

import jakarta.inject.Inject;

@Creatable
public class PromptContextValueProvider 
{
    
    public static final String GIT_DIFF = "gitDiff";
    public static final String CONSOLE_OUTPUT = "consoleOutput";
    public static final String ERRORS = "errors";
    public static final String SELECTED_CONTENT = "selectedContent";
    public static final String CURRENT_FILE_CONTENT = "currentFileContent";
    public static final String CURRENT_FILE_PATH = "currentFilePath";
    public static final String CURRENT_FILE_NAME = "currentFileName";
    public static final String CURRENT_PROJECT_NAME = "currentProjectName";
    
    // Completion-specific context keys
    private static final String FILE_EXTENSION = "fileExtension";
    private static final String CODE_BEFORE_CURSOR = "codeBeforeCursor";
    private static final String CODE_AFTER_CURSOR = "codeAfterCursor";
    private static final String CURSOR_LINE = "cursorLine";
    private static final String CURSOR_COLUMN = "cursorColumn";
    
    @Inject
    ILog logger;
    @Inject
    UISynchronizeCallable uiSync;
	@Inject
	private CodeAnalysisService codeAnalysisService;
	@Inject
	private EditorService editorService;
	@Inject
	private ConsoleService consoleService;
	@Inject
	private GitService gitService;
	@Inject
	private ResourceCache resourceCache;
	
	public String getContextValue( String key )
	{
	    Objects.requireNonNull( key );
	    
	    
	    return switch (  key ) {
            case CURRENT_PROJECT_NAME -> safeGetString( () -> editorService.getCurrentlyOpenedFile().map( IFile::getProject ).map(IProject::getName).orElse( "" ) );
	        case CURRENT_FILE_PATH -> safeGetString( () -> editorService.getCurrentlyOpenedFile().map( IFile::getProjectRelativePath ).map(IPath::toString).orElse( "" ) );
            case CURRENT_FILE_NAME -> safeGetString( () -> editorService.getCurrentlyOpenedFile().map( IFile::getName ).orElse( "" ) );
	        case CURRENT_FILE_CONTENT -> safeGetString(() -> cacheResource( editorService.getCurrentlyOpenedFileContentWithResource() )  );
	        case SELECTED_CONTENT -> safeGetString(() -> editorService.getEditorSelection() );
	        case ERRORS -> safeGetString( () -> {
	            String project = getContextValue( CURRENT_PROJECT_NAME );
	            String file = getContextValue( CURRENT_FILE_PATH );
	            if ( file == null || file.isBlank() )
	            {
	                return codeAnalysisService.getCompilationErrors( project, "ERROR", -1, null );
	            }
	            return codeAnalysisService.getCompilationErrors( project, "ERROR", -1, file );
	        } );
	        case CONSOLE_OUTPUT -> safeGetString( () -> cacheResource( consoleService.getConsoleOutputWithResource( null, 100, true) ) ) ;
            case GIT_DIFF -> safeGetString( () -> gitService.getCurrentDiff() ) ;
            case FILE_EXTENSION -> safeGetString( () -> editorService.getCurrentlyOpenedFile().map( IFile::getFileExtension ).orElse( "" ) );
            case CODE_BEFORE_CURSOR -> safeGetString( () -> editorService.getCodeBeforeCursor() );
            case CODE_AFTER_CURSOR -> safeGetString( () -> editorService.getCodeAfterCursor() );
            case CURSOR_LINE -> safeGetString( () -> editorService.getCursorLine() );
            case CURSOR_COLUMN -> safeGetString( () -> editorService.getCursorColumn() );
	        default -> {
                logger.warn("Unknown context key: " + key);
                yield "";
	        }
	    };
	}
	
	private String cacheResource( ResourceToolResult resource )
	{
	    if ( resource.isCacheable() )
	    {
	        var cached = resourceCache.put( resource );
            return String.format( 
                    "[Resource cached: %s (version %d, ~%d tokens)]\n" +
                    "Content available in <resources> block at top of context.",
                    cached.descriptor().uri(),
                    cached.version(),
                    cached.estimateTokens()
                );
	    }
	    return resource.getContent();
	}
	
    /**
     * Safely executes a supplier function and handles any exceptions.
     * 
     * @param supplier The function that provides the context value
     * @return The context value, or empty string if an error occurs
     */
	private String safeGetString( Callable<String> supplier )
	{
	    try
	    {
	        return uiSync.syncCall( supplier );
	    }
	    catch ( Exception e )
	    {
	        logger.error( e.getMessage(), e );
	        return "";
	    }
	}
	
	
	
	
}
