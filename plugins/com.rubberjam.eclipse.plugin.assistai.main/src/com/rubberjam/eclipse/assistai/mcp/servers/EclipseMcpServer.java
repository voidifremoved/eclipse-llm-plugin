package com.rubberjam.eclipse.assistai.mcp.servers;

import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.mcp.annotations.McpServer;
import com.rubberjam.eclipse.assistai.mcp.annotations.Tool;
import com.rubberjam.eclipse.assistai.mcp.annotations.ToolParam;
import com.rubberjam.eclipse.assistai.mcp.services.CodeAnalysisService;
import com.rubberjam.eclipse.assistai.mcp.services.CodeEditingService;
import com.rubberjam.eclipse.assistai.mcp.services.ConsoleService;
import com.rubberjam.eclipse.assistai.mcp.services.EditorService;
import com.rubberjam.eclipse.assistai.mcp.services.GitService;
import com.rubberjam.eclipse.assistai.mcp.services.JavaDocService;
import com.rubberjam.eclipse.assistai.mcp.services.JavaLaunchService;
import com.rubberjam.eclipse.assistai.mcp.services.LocalHistoryService;
import com.rubberjam.eclipse.assistai.mcp.services.MarkdownService;
import com.rubberjam.eclipse.assistai.mcp.services.MavenService;
import com.rubberjam.eclipse.assistai.mcp.services.OutlineService;
import com.rubberjam.eclipse.assistai.mcp.services.PDEService;
import com.rubberjam.eclipse.assistai.mcp.services.ProjectService;
import com.rubberjam.eclipse.assistai.mcp.services.ResourceService;
import com.rubberjam.eclipse.assistai.mcp.services.SearchService;
import com.rubberjam.eclipse.assistai.mcp.services.UnitTestService;
import com.rubberjam.eclipse.assistai.resources.CachedResource;
import com.rubberjam.eclipse.assistai.resources.ResourceCache;
import com.rubberjam.eclipse.assistai.resources.ResourceResultSerializer;
import com.rubberjam.eclipse.assistai.resources.ResourceToolResult;

import jakarta.inject.Inject;

@Creatable
@McpServer(name = "eclipse")
public class EclipseMcpServer
{
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter
            .ofPattern( "yyyy-MM-dd HH:mm:ss" )
            .withZone( ZoneId.systemDefault() );

    @Inject
    private JavaDocService javaDocService;

    @Inject
    private ProjectService projectService;

    @Inject
    private CodeAnalysisService codeAnalysisService;

    @Inject
    private ResourceService resourceService;

    @Inject
    private SearchService searchService;

    @Inject
    private EditorService editorService;

    @Inject
    private ConsoleService consoleService;

    @Inject
    private CodeEditingService codeEditingService;

    @Inject
    private UnitTestService unitTestService;

    @Inject
    private MavenService mavenService;

    @Inject
    private OutlineService outlineService;

    @Inject
    private MarkdownService markdownService;

    @Inject
    private JavaLaunchService javaLaunchService;

    @Inject
    private ResourceCache resourceCache;

    @Inject
    private LocalHistoryService localHistoryService;

    @Inject
    private GitService gitService;

    @Inject
    private PDEService pdeService;

    // --- Code Editing Tools (formerly eclipse-coder) ---

    @Tool(name="createFile", description="Create and open a new file in a specified project. Ensure the file doesn't already exist.", type="object")
    public String createFile(
        @ToolParam(name="projectName", description="The name of the project where the file should be created", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the file relative to the project root. Do not include project name!", required=true) String filePath,
        @ToolParam(name="content", description="The content to write to the file", required=true) String content) 
    {
        return codeEditingService.createFileAndOpen(projectName, filePath, content);
    }

    @Tool(name="insertIntoFile", description="Insert content into a file at a specified line position, using 1-based line indexing. The new content will be inserted BEFORE the specified line, and existing content at that line and below will be shifted down.", type="object")
    public String insertIntoFile(
        @ToolParam(name = "projectName", description = "The name of the project containing the file", required = true) String projectName,
        @ToolParam(name = "filePath", description = "The path to the file relative to the project root. Do not include project name!", required = true) String filePath,
        @ToolParam(name = "content", description = "The content to insert into the file", required = true) String content,
        @ToolParam(name = "line", description = "The line number before which to insert the text (1-based index). Existing content at this line and below will be shifted down. Use line=1 to insert at the beginning of the file.", required = false) String line) 
    {
        int lineNum = Optional.ofNullable(line).map(Integer::parseInt).orElse(0);
        return codeEditingService.insertIntoFile(projectName, filePath, content, lineNum);
    }

    @Tool(name="replaceString", description="Find and replace a specific string in a file, with optional line range for targeted replacement.", type="object")
    public String replaceString(
        @ToolParam(name="projectName", description="The name of the project containing the file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the file relative to the project root. Do not include project name!", required=true) String filePath,
        @ToolParam(name="oldString", description="The text to replace (must match exactly, including whitespace and indentation)", required=true) String oldString,
        @ToolParam(name="newString", description="The new text to insert in place of the old text", required=true) String newString,
        @ToolParam(name="startLine", description="Optional line number to start searching from (1-based index)", required=false) String startLine,
        @ToolParam(name="endLine", description="Optional line number to end searching at (1-based index)", required=false) String endLine) 
    {
        Integer start = Optional.ofNullable(startLine).map(Integer::parseInt).orElse(0);
        Integer end = Optional.ofNullable(endLine).map(Integer::parseInt).orElse(0);
        return codeEditingService.replaceStringInFile(projectName, filePath, oldString, newString, start, end);
    }

    @Tool(name="undoEdit", description="Undoes the last edit operation by restoring a file from its backup.", type="object")
    public String undoEdit(
        @ToolParam(name="projectName", description="The name of the project containing the file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the file relative to the project root. Do not include project name!", required=true) String filePath) 
    {
        return codeEditingService.undoEdit(projectName, filePath);
    }

    @Tool(name="createDirectories", description="Creates a directory structure (recursively) in the specified project.", type="object")
    public String createDirectories(
        @ToolParam(name="projectName", description="The name of the project where directories should be created", required=true) String projectName,
        @ToolParam(name="directoryPath", description="The path of directories to create, relative to the project root. Do not include project name!", required=true) String directoryPath) 
    {
        return codeEditingService.createDirectories(projectName, directoryPath);
    }

    @Tool(name="renameFile", description="Renames a file in the specified project.", type="object")
    public String renameFile(
        @ToolParam(name="projectName", description="The name of the project containing the file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the file relative to the project root. Do not include project name!", required=true) String filePath,
        @ToolParam(name="newFileName", description="The new name for the file", required=true) String newFileName) 
    {
        return codeEditingService.renameFile(projectName, filePath, newFileName);
    }

    @Tool(name="refactorRenameJavaType", description="Renames a Java class/interface/enum using Eclipse's refactoring mechanism. This updates the type name, file name, and ALL references throughout the workspace. Use this instead of renameFile for Java files to ensure all references are updated correctly.", type="object")
    public String refactorRenameJavaType(
        @ToolParam(name="projectName", description="The name of the project containing the Java file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the Java file relative to the project root (e.g., 'src/com/example/MyClass.java')", required=true) String filePath,
        @ToolParam(name="newTypeName", description="The new name for the Java type (without .java extension, e.g., 'NewClassName')", required=true) String newTypeName) 
    {
        return codeEditingService.refactorRenameJavaType(projectName, filePath, newTypeName);
    }

    @Tool(name="refactorMoveJavaType", description="Moves a Java class/interface/enum to a different package using Eclipse's refactoring mechanism. This updates the package declaration and ALL references throughout the workspace. The target package will be created if it doesn't exist.", type="object")
    public String refactorMoveJavaType(
        @ToolParam(name="projectName", description="The name of the project containing the Java file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the Java file relative to the project root (e.g., 'src/com/example/MyClass.java')", required=true) String filePath,
        @ToolParam(name="targetPackage", description="The fully qualified target package name (e.g., 'com.example.newpackage')", required=true) String targetPackage) 
    {
        return codeEditingService.refactorMoveJavaType(projectName, filePath, targetPackage);
    }

    @Tool(name="refactorRenamePackage", description="Renames a Java package using Eclipse's refactoring mechanism. This renames the package directory, updates all package declarations in contained files, and updates ALL references throughout the workspace.", type="object")
    public String refactorRenamePackage(
        @ToolParam(name="projectName", description="The name of the project containing the package", required=true) String projectName,
        @ToolParam(name="packageName", description="The current fully qualified package name (e.g., 'com.example.oldpackage')", required=true) String packageName,
        @ToolParam(name="newPackageName", description="The new package name - can be fully qualified (e.g., 'com.example.newpackage') or just the last segment to rename", required=true) String newPackageName) 
    {
        return codeEditingService.refactorRenamePackage(projectName, packageName, newPackageName);
    }

    @Tool(name="moveResource", description="Moves a file or folder to a different location within the project. For Java files, prefer using refactorMoveJavaType instead to ensure all references are updated.", type="object")
    public String moveResource(
        @ToolParam(name="projectName", description="The name of the project containing the resource", required=true) String projectName,
        @ToolParam(name="sourcePath", description="The path to the file or folder relative to the project root", required=true) String sourcePath,
        @ToolParam(name="targetPath", description="The target directory path relative to the project root where the resource should be moved to", required=true) String targetPath) 
    {
        return codeEditingService.moveResource(projectName, sourcePath, targetPath);
    }

    @Tool(name="organizeImports", description="Organizes imports in a Java file using Eclipse's organize imports mechanism. This removes unused imports, adds missing imports, and sorts them according to project settings. Equivalent to pressing Ctrl+Shift+O in Eclipse.", type="object")
    public String organizeImports(
        @ToolParam(name="projectName", description="The name of the project containing the Java file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the Java file relative to the project root (e.g., 'src/com/example/MyClass.java')", required=true) String filePath) 
    {
        return codeEditingService.organizeImports(projectName, filePath);
    }

    @Tool(name="organizeImportsInPackage", description="Organizes imports in all Java files within a package. This is useful for cleaning up imports across multiple files at once.", type="object")
    public String organizeImportsInPackage(
        @ToolParam(name="projectName", description="The name of the project containing the package", required=true) String projectName,
        @ToolParam(name="packageName", description="The fully qualified package name (e.g., 'com.example.mypackage')", required=true) String packageName) 
    {
        return codeEditingService.organizeImportsInPackage(projectName, packageName);
    }

    @Tool(name="deleteFile", description="Deletes a file from the specified project.", type="object")
    public String deleteFile(
        @ToolParam(name="projectName", description="The name of the project containing the file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the file relative to the project root. Do not include project name!", required=true) String filePath) 
    {
        return codeEditingService.deleteFile(projectName, filePath);
    }

    @Tool(name="replaceFileContent", description="Replaces the entire content of a file with new content.", type="object")
    public String replaceFileContent(
        @ToolParam(name="projectName", description="The name of the project containing the file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the file relative to the project root. Do not include project name!", required=true) String filePath,
        @ToolParam(name="content", description="The new content to write to the file", required=true) String content) 
    {
        return codeEditingService.replaceFileContent(projectName, filePath, content);
    }

    @Tool(name="deleteLinesInFile", description="Deletes a range of lines in a file, using 1-based line indexing.", type="object")
    public String deleteLinesInFile(
        @ToolParam(name="projectName", description="The name of the project containing the file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the file relative to the project root. Do not include project name!", required=true) String filePath,
        @ToolParam(name="startLine", description="The line number to start deletion from (1-based index)", required=true) String startLine,
        @ToolParam(name="endLine", description="The line number to end deletion at (inclusive, 1-based index)", required=true) String endLine) 
    {
        int startLineNum = Integer.parseInt(startLine);
        int endLineNum = Integer.parseInt(endLine);
        return codeEditingService.deleteLinesInFile(projectName, filePath, startLineNum, endLineNum);
    }

    @Tool(name="applyPatch", description="Applies a unified diff patch to a file. The patch should be in standard unified diff format with @@ hunk headers. Context lines are used for fuzzy matching, so the patch can be applied even if line numbers have shifted. This is more reliable than replaceString for multi-hunk edits. Optionally shows Eclipse's Apply Patch dialog for user review.", type="object")
    public String applyPatch(
        @ToolParam(name="projectName", description="The name of the project containing the file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the file relative to the project root. Do not include project name!", required=true) String filePath,
        @ToolParam(name="patch", description="The unified diff content to apply. Should contain @@ hunk headers and lines prefixed with ' ' (context), '-' (remove), or '+' (add). File headers (--- and +++) are optional.", required=true) String patch,
        @ToolParam(name="showDialog", description="If 'true', shows Eclipse's Apply Patch wizard dialog for user review instead of applying directly. Default is 'false'.", required=false) String showDialog)
    {
        boolean showPatchDialog = Optional.ofNullable(showDialog).map(Boolean::parseBoolean).orElse(false);
        return codeEditingService.applyPatch(projectName, filePath, patch, showPatchDialog);
    }

    @Tool(name="formatFile", description="Formats an entire Java file using Eclipse's code formatter (equivalent to Ctrl+Shift+F). Applies the project-specific or workspace formatter settings.", type="object")
    public String formatFile(
        @ToolParam(name="projectName", description="The name of the project containing the file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the Java file relative to the project root. Do not include project name!", required=true) String filePath)
    {
        return codeEditingService.formatFile(projectName, filePath);
    }

    @Tool(name="refactorRenameJavaField", description="Renames a Java class field/variable using Eclipse's refactoring mechanism. This updates the field declaration and ALL references/usages throughout the workspace.", type="object")
    public String refactorRenameJavaField(
        @ToolParam(name="projectName", description="The name of the project containing the Java file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the Java file relative to the project root", required=true) String filePath,
        @ToolParam(name="oldFieldName", description="The current name of the field to rename", required=true) String oldFieldName,
        @ToolParam(name="newFieldName", description="The new name for the field", required=true) String newFieldName) 
    {
        return codeEditingService.refactorRenameJavaField(projectName, filePath, oldFieldName, newFieldName);
    }

    @Tool(name="refactorRenameJavaMethod", description="Renames a Java method using Eclipse's refactoring mechanism. This updates the method signature and ALL callers/references throughout the workspace. If the method is overloaded, this will target the method with the specified name.", type="object")
    public String refactorRenameJavaMethod(
        @ToolParam(name="projectName", description="The name of the project containing the Java file", required=true) String projectName,
        @ToolParam(name="filePath", description="The path to the Java file relative to the project root", required=true) String filePath,
        @ToolParam(name="oldMethodName", description="The current name of the method to rename", required=true) String oldMethodName,
        @ToolParam(name="newMethodName", description="The new name for the method", required=true) String newMethodName) 
    {
        return codeEditingService.refactorRenameJavaMethod(projectName, filePath, oldMethodName, newMethodName);
    }

    // --- Integration Tools (formerly eclipse-ide) ---

    @Tool(name = "formatCode", description = "Formats code according to the current Eclipse formatter settings.", type = "object")
    public String formatCode(
            @ToolParam(name = "code", description = "The code to be formatted", required = true) String code,
            @ToolParam(name = "projectName", description = "Optional project name to use project-specific formatter settings", required = false) String projectName)
    {
        return codeEditingService.formatCode(code, projectName);
    }

    @Tool(name = "getJavaDoc", description = "Get the JavaDoc for the given compilation unit.  For example,a class B defined as a member type of a class A in package x.y should have athe fully qualified name \"x.y.A.B\".Note that in order to be found, a type name (or its top level enclosingtype name) must match its corresponding compilation unit name.", type = "object")
    public String getJavaDoc(
            @ToolParam(name = "fullyQualifiedName", description = "A fully qualified name of the compilation unit", required = true) String fullyQualifiedClassName)
    {
        return javaDocService.getJavaDoc(fullyQualifiedClassName);
    }

    @Tool(name = "getSource", description = "Get the source for the given class.", type = "object")
    public String getSource(
            @ToolParam(name = "fullyQualifiedClassName", description = "A fully qualified class name of the Java class", required = true) String fullyQualifiedClassName)
    {
        ResourceToolResult result = javaDocService.getSourceWithResource(fullyQualifiedClassName);
        return ResourceResultSerializer.serialize(result);
    }

    @Tool(name = "getClassOutline", description = "Returns a compact outline of a Java class: class declaration, field declarations, method signatures (no bodies), and inner types â€” all with line numbers. Much more token-efficient than getSource for understanding class structure. Use this first, then getMethodSource for specific methods.", type = "object")
    public String getClassOutline(
            @ToolParam(name = "fullyQualifiedClassName", description = "A fully qualified class name (e.g. 'com.example.MyClass')", required = true) String fullyQualifiedClassName,
            @ToolParam(name = "includeFields", description = "Whether to include field declarations (default: true)", required = false) String includeFields)
    {
        boolean fields = Optional.ofNullable(includeFields).map(Boolean::parseBoolean).orElse(true);
        ResourceToolResult result = outlineService.getClassOutline(fullyQualifiedClassName, fields);
        return ResourceResultSerializer.serialize(result);
    }

    @Tool(name = "getMethodSource", description = "Returns the source code of specific method(s) with line numbers. Accepts comma-separated method names to retrieve multiple methods in one call. Use after getClassOutline to read only the methods you need.", type = "object")
    public String getMethodSource(
            @ToolParam(name = "fullyQualifiedClassName", description = "A fully qualified class name (e.g. 'com.example.MyClass')", required = true) String fullyQualifiedClassName,
            @ToolParam(name = "methodNames", description = "Comma-separated method names to retrieve (e.g. 'findById,save,delete')", required = true) String methodNames,
            @ToolParam(name = "methodSignature", description = "Optional parameter type hint to disambiguate overloaded methods (e.g. 'String')", required = false) String methodSignature,
            @ToolParam(name = "includeJavadoc", description = "Whether to include Javadoc comments (default: true)", required = false) String includeJavadoc)
    {
        boolean javadoc = Optional.ofNullable(includeJavadoc).map(Boolean::parseBoolean).orElse(true);
        ResourceToolResult result = outlineService.getMethodSource(fullyQualifiedClassName, methodNames, methodSignature, javadoc);
        return ResourceResultSerializer.serialize(result);
    }

    @Tool(name = "getFilteredSource", description = "Returns source code with optional import exclusion and selective method expansion. Methods not in the expand list are collapsed to their signature with line ranges. Line numbers always match the original file for accurate editing.", type = "object")
    public String getFilteredSource(
            @ToolParam(name = "fullyQualifiedClassName", description = "A fully qualified class name (e.g. 'com.example.MyClass')", required = true) String fullyQualifiedClassName,
            @ToolParam(name = "excludeImports", description = "Whether to collapse the import block (default: true)", required = false) String excludeImports,
            @ToolParam(name = "methodNames", description = "Comma-separated method names to fully expand. Methods not listed are collapsed to signatures. If omitted, all methods are expanded.", required = false) String methodNames)
    {
        boolean noImports = Optional.ofNullable(excludeImports).map(Boolean::parseBoolean).orElse(true);
        ResourceToolResult result = outlineService.getFilteredSource(fullyQualifiedClassName, noImports, methodNames);
        return ResourceResultSerializer.serialize(result);
    }

    @Tool(name = "getProjectProperties", description = "Retrieves the properties and configuration of a specified project.", type = "object")
    public String getProjectProperties(
            @ToolParam(name = "projectName", description = "The name of the project to analyze", required = true) String projectName)
    {
        return projectService.getProjectProperties(projectName);
    }

    @Tool(name = "getProjectLayout", description = "Get the file and folder structure of a specified project in a hierarchical format. For large projects, use scopePath to limit to a subdirectory and/or maxDepth to limit tree depth.", type = "object")
    public String getProjectLayout(
            @ToolParam(name = "projectName", description = "The name of the project to analyze", required = true) String projectName,
            @ToolParam(name = "scopePath", description = "Optional path relative to the project root to limit the listing (e.g., 'src/main/java/com/example'). If omitted, shows the entire project.", required = false) String scopePath,
            @ToolParam(name = "maxDepth", description = "Optional maximum depth of the directory tree to display (e.g., '3' for 3 levels deep). If omitted, shows all levels.", required = false) String maxDepth)
    {
        int depth = Optional.ofNullable(maxDepth).map(Integer::parseInt).orElse(-1);
        ResourceToolResult result = projectService.getProjectLayoutWithResource(projectName, scopePath, depth);
        return ResourceResultSerializer.serialize(result);
    }

    @Tool(name = "getMethodCallHierarchy", description = "Retrieves the call hierarchy (callers) for a specified method to understand how it's used in the codebase.", type = "object")
    public String getMethodCallHierarchy(
            @ToolParam(name = "fullyQualifiedClassName", description = "The fully qualified name of the class containing the method", required = true) String fullyQualifiedClassName,
            @ToolParam(name = "methodName", description = "The name of the method to analyze", required = true) String methodName,
            @ToolParam(name = "methodSignature", description = "The signature of the method (optional, required if method is overloaded)", required = false) String methodSignature,
            @ToolParam(name = "maxDepth", description = "Maximum depth of the call hierarchy to retrieve (default: 3)", required = false) String maxDepth)
    {
        return codeAnalysisService.getMethodCallHierarchy(fullyQualifiedClassName, methodName, methodSignature,
                Optional.ofNullable(maxDepth).map(Integer::parseInt).orElse(0));
    }

    @Tool(name = "getCompilationErrors", description = "Retrieves compilation errors and problems from the workspace, a project, or a single file. When the user asks to fix the current/open file, pass filePath (project-relative path from the system prompt).", type = "object")
    public String getCompilationErrors(
            @ToolParam(name = "projectName", description = "The name of the specific project to check (optional, leave empty for all projects)", required = false) String projectName,
            @ToolParam(name = "severity", description = "Filter by severity level: 'ERROR', 'WARNING', or 'ALL' (default)", required = false) String severity,
            @ToolParam(name = "maxResults", description = "Maximum number of problems to return (default: 50)", required = false) String maxResults,
            @ToolParam(name = "filePath", description = "Optional project-relative file path (e.g. src/com/example/Foo.java). When set, only problems in that file are returned.", required = false) String filePath)
    {
        return codeAnalysisService.getCompilationErrors(
                projectName,
                severity,
                Optional.ofNullable( maxResults ).map( Integer::parseInt ).orElse( 0 ),
                filePath );
    }

    @Tool(name = "readProjectResource", description = "Read the content of a text resource from a specified project. Supports line numbers, reading specific line ranges, and collapsing Java imports to reduce token usage.", type = "object")
    public String readProjectResource(
            @ToolParam(name = "projectName", description = "The name of the project containing the resource", required = true) String projectName,
            @ToolParam(name = "resourcePath", description = "The path to the resource relative to the project root", required = true) String resourcePath,
            @ToolParam(name = "showLineNumbers", description = "If 'true', prepends line numbers to each line (like cat -n). Useful for creating accurate patches. Default: 'false'", required = false) String showLineNumbers,
            @ToolParam(name = "startLine", description = "Optional 1-based start line to read from. If omitted, reads from the beginning.", required = false) String startLine,
            @ToolParam(name = "endLine", description = "Optional 1-based end line to read to (inclusive). If omitted, reads to the end.", required = false) String endLine,
            @ToolParam(name = "excludeImports", description = "If 'true', collapses Java import statements into a single summary line. Line numbers are preserved for accurate editing. Default: 'false'", required = false) String excludeImports)
    {
        boolean lineNumbers = Optional.ofNullable(showLineNumbers).map(Boolean::parseBoolean).orElse(false);
        int start = Optional.ofNullable(startLine).map(Integer::parseInt).orElse(0);
        int end = Optional.ofNullable(endLine).map(Integer::parseInt).orElse(0);
        boolean noImports = Optional.ofNullable(excludeImports).map(Boolean::parseBoolean).orElse(false);
        ResourceToolResult result = resourceService.readProjectResourceWithResource(projectName, resourcePath, lineNumbers, start, end, noImports);
        return ResourceResultSerializer.serialize(result);
    }

    @Tool(name = "listProjects", description = "List all available projects in the workspace with their detected natures (Java, C/C++, Python, etc.).", type = "object")
    public String listProjects()
    {
        return projectService.listProjects();
    }

    @Tool(name = "getCurrentlyOpenedFile", description = "Gets information about the currently active file in the Eclipse editor.", type = "object")
    public String getCurrentlyOpenedFile()
    {
        ResourceToolResult result = editorService.getCurrentlyOpenedFileContentWithResource();
        return ResourceResultSerializer.serialize(result);
    }

    @Tool(name = "getEditorSelection", description = "Gets the currently selected text or lines in the active editor.", type = "object")
    public String getEditorSelection()
    {
        return editorService.getEditorSelection();
    }

    @Tool(name = "getConsoleOutput", description = "Retrieves the recent output from Eclipse console(s).", type = "object")
    public String getConsoleOutput(
            @ToolParam(name = "consoleName", description = "Name of the specific console to retrieve (optional, leave empty for all or most recent console)", required = false) String consoleName,
            @ToolParam(name = "maxLines", description = "Maximum number of lines to retrieve (default: 100)", required = false) String maxLines,
            @ToolParam(name = "includeAllConsoles", description = "Whether to include output from all available consoles (default: false)", required = false) Boolean includeAllConsoles)
    {
        ResourceToolResult result = consoleService.getConsoleOutputWithResource(consoleName,
                Optional.ofNullable(maxLines).map(Integer::parseInt).orElse(0), includeAllConsoles);
        return ResourceResultSerializer.serialize(result);
    }

    @Tool(name = "runAllTests", description = "Runs all JUnit tests in a specified project and returns the results. Use findTestClasses first if unsure which project contains tests. The projectName must be the test project (e.g. 'my.app.tests'), not the main source project.", type = "object")
    public String runAllTests(
            @ToolParam(name = "projectName", description = "The exact Eclipse project name containing the test classes (use listProjects to find it)", required = true) String projectName,
            @ToolParam(name = "timeout", description = "Maximum time in seconds to wait for test completion (default: 60)", required = false) String timeout)
    {
        return unitTestService.runAllTests(projectName, Optional.ofNullable(timeout).map(Integer::parseInt).orElse(60));
    }

    @Tool(name = "runPackageTests", description = "Runs all JUnit tests in a specific package and returns the results.", type = "object")
    public String runPackageTests(
            @ToolParam(name = "projectName", description = "The exact Eclipse project name containing the test classes (use listProjects to find it)", required = true) String projectName,
            @ToolParam(name = "packageName", description = "The fully qualified package name (e.g. 'com.example.service')", required = true) String packageName,
            @ToolParam(name = "timeout", description = "Maximum time in seconds to wait for test completion (default: 60)", required = false) String timeout)
    {
        return unitTestService.runPackageTests(projectName, packageName,
                Optional.ofNullable(timeout).map(Integer::parseInt).orElse(60));
    }

    @Tool(name = "runClassTests", description = "Runs all JUnit tests in a specific test class and returns the results.", type = "object")
    public String runClassTests(
            @ToolParam(name = "projectName", description = "The exact Eclipse project name containing the test class (use listProjects to find it)", required = true) String projectName,
            @ToolParam(name = "className", description = "The fully qualified class name including package (e.g. 'com.example.MyServiceTest')", required = true) String className,
            @ToolParam(name = "timeout", description = "Maximum time in seconds to wait for test completion (default: 60)", required = false) String timeout)
    {
        return unitTestService.runClassTests(projectName, className,
                Optional.ofNullable(timeout).map(Integer::parseInt).orElse(60));
    }

    @Tool(name = "runTestMethod", description = "Runs a single JUnit test method and returns the results.", type = "object")
    public String runTestMethod(
            @ToolParam(name = "projectName", description = "The exact Eclipse project name containing the test class (use listProjects to find it)", required = true) String projectName,
            @ToolParam(name = "className", description = "The fully qualified class name including package (e.g. 'com.example.MyServiceTest')", required = true) String className,
            @ToolParam(name = "methodName", description = "The test method name without parentheses (e.g. 'testCreate')", required = true) String methodName,
            @ToolParam(name = "timeout", description = "Maximum time in seconds to wait for test completion (default: 60)", required = false) String timeout)
    {
        return unitTestService.runTestMethod(projectName, className, methodName,
                Optional.ofNullable(timeout).map(Integer::parseInt).orElse(60));
    }

    @Tool(name = "findTestClasses", description = "Finds all test classes in a project. Use this before runAllTests or runClassTests to discover the correct project name and fully qualified class names.", type = "object")
    public String findTestClasses(
            @ToolParam(name = "projectName", description = "The exact Eclipse project name to search (use listProjects to find it)", required = true) String projectName)
    {
        return unitTestService.findTestClasses(projectName);
    }

    @Tool(name = "runMavenBuild", description = "Runs a Maven build with the specified goals on a project.", type = "object")
    public String runMavenBuild(
            @ToolParam(name = "projectName", description = "The name of the project to build", required = true) String projectName,
            @ToolParam(name = "goals", description = "The Maven goals to execute (e.g., \"clean install\")", required = true) String goals,
            @ToolParam(name = "profiles", description = "Optional Maven profiles to activate", required = false) String profiles,
            @ToolParam(name = "timeout", description = "Maximum time in seconds to wait for build completion (0 for no timeout)", required = false) String timeout)
    {
        return mavenService.runMavenBuild(projectName, goals, profiles,
                Optional.ofNullable(timeout).map(Integer::parseInt).orElse(0));
    }

    @Tool(name = "getEffectivePom", description = "Gets the effective POM for a Maven project.", type = "object")
    public String getEffectivePom(
            @ToolParam(name = "projectName", description = "The name of the Maven project", required = true) String projectName)
    {
        return mavenService.getEffectivePom(projectName);
    }

    @Tool(name = "listMavenProjects", description = "Lists all available Maven projects in the workspace.", type = "object")
    public String listMavenProjects()
    {
        return mavenService.listMavenProjects();
    }

    @Tool(name = "getProjectDependencies", description = "Gets Maven project dependencies.", type = "object")
    public String getProjectDependencies(
            @ToolParam(name = "projectName", description = "The name of the Maven project", required = true) String projectName)
    {
        return mavenService.getProjectDependencies(projectName);
    }

    @Tool(name = "updateMavenProject", description = "Updates Maven project configurations/dependencies (equivalent to Eclipse's Alt+F5 Update Project). Useful when dependencies in pom.xml have been updated and Eclipse has compilation errors.", type = "object")
    public String updateMavenProject(
            @ToolParam(name = "projectName", description = "The name of the Maven project to update", required = true) String projectName)
    {
        return mavenService.updateMavenProject(projectName);
    }

    @Tool(name = "getTypeHierarchy", description = "Retrieves the type hierarchy (supertypes, implemented interfaces, and subtypes) for a given Java class or interface.", type = "object")
    public String getTypeHierarchy(
            @ToolParam(name = "fullyQualifiedClassName", description = "The fully qualified name of the class (e.g., 'com.example.MyClass')", required = true) String fullyQualifiedClassName)
    {
        return codeAnalysisService.getTypeHierarchy(fullyQualifiedClassName);
    }

    @Tool(name = "findReferences", description = "Finds all references/usages of a Java type, method, or field across the entire workspace. Essential before renaming or deleting code elements.", type = "object")
    public String findReferences(
            @ToolParam(name = "fullyQualifiedClassName", description = "The fully qualified name of the class containing the element", required = true) String fullyQualifiedClassName,
            @ToolParam(name = "elementName", description = "Optional method or field name to search for. If omitted, searches for references to the class itself.", required = false) String elementName)
    {
        return codeAnalysisService.findReferences(fullyQualifiedClassName, elementName);
    }

    @Tool(name = "executeQuickFix", description = "Applies a specific quick fix proposal to a compilation problem. Use getCompilationErrors first to obtain the Marker ID and proposal index.", type = "object")
    public String executeQuickFix(
            @ToolParam(name = "markerId", description = "The Marker ID of the problem (from getCompilationErrors)", required = true) String markerId,
            @ToolParam(name = "proposalIndex", description = "The 0-based index of the quick fix proposal to apply (from the quick fixes list)", required = true) String proposalIndex)
    {
        return codeAnalysisService.executeQuickFix(Long.parseLong(markerId), Integer.parseInt(proposalIndex));
    }

    @Tool(name = "getImportSuggestions", description = "Finds import candidates for unresolved types in a Java file. Shows matching fully qualified names from the workspace for each unresolved type error.", type = "object")
    public String getImportSuggestions(
            @ToolParam(name = "projectName", description = "The name of the project containing the file", required = true) String projectName,
            @ToolParam(name = "filePath", description = "The path to the Java file relative to the project root", required = true) String filePath)
    {
        return codeAnalysisService.getImportSuggestions(projectName, filePath);
    }

    @Tool(name = "fileSearch", description = "Searches for a plain substring in workspace files using Eclipse's text search engine.", type = "object")
    public String fileSearch(
            @ToolParam(name = "containingText", description = "Text that must be contained in a line (plain substring, not regex)", required = true) String containingText,
            @ToolParam(name = "fileNamePatterns", description = "Optional file name patterns. Accepts either an array (e.g. [\"*.java\", \"*.xml\"]) or a string (e.g. \"*.java,*.xml\"). If omitted, all files are searched.", required = false) Object fileNamePatterns)
    {
        String[] patterns = normalizeFileNamePatterns(fileNamePatterns);
        return searchService.fileSearch(containingText, patterns).toString();
    }

    @Tool(name = "fileSearchRegExp", description = "Searches workspace files using a Java regular expression via Eclipse's text search engine.", type = "object")
    public String fileSearchRegExp(
            @ToolParam(name = "pattern", description = "Java regular expression", required = true) String pattern,
            @ToolParam(name = "fileNamePatterns", description = "Optional file name patterns. Accepts either an array (e.g. [\"*.java\", \"*.xml\"]) or a string (e.g. \"*.java,*.xml\"). If omitted, all files are searched.", required = false) Object fileNamePatterns)
    {
        String[] patterns = normalizeFileNamePatterns(fileNamePatterns);
        return searchService.fileSearchRegExp(pattern, patterns).toString();
    }

    @Tool(name = "findFiles", description = "Finds workspace files matching the given glob patterns.", type = "object")
    public String findFiles(
            @ToolParam(name = "fileNamePatterns", description = "Glob patterns. Accepts either an array (e.g. [\"*.java\", \"pom.xml\"]) or a string (e.g. \"*.java, pom.xml\"). If omitted, defaults to '*'", required = false) Object fileNamePatterns,
            @ToolParam(name = "maxResults", description = "Maximum number of results to return (default: 200)", required = false) String maxResults)
    {
        String[] patterns = normalizeFileNamePatterns(fileNamePatterns);
        int limit = Optional.ofNullable(maxResults).map(Integer::parseInt).orElse(0);
        return resourceService.findFiles(patterns, limit).toString();
    }

    @Tool(name = "searchAndReplace", description = "Search and replace across multiple files in the workspace using Eclipse's text search engine.", type = "object")
    public String searchAndReplace(
            @ToolParam(name = "containingText", description = "Plain text to find (not regex)", required = true) String containingText,
            @ToolParam(name = "replacementText", description = "Replacement text (can be empty)", required = true) String replacementText,
            @ToolParam(name = "fileNamePatterns", description = "Optional file name patterns. Accepts either an array (e.g. [\"*.java\", \"*.xml\"]) or a string (e.g. \"*.java,*.xml\"). If omitted, all files are searched.", required = false) Object fileNamePatterns)
    {
        String[] patterns = normalizeFileNamePatterns(fileNamePatterns);
        return searchService.searchAndReplace(containingText, replacementText, patterns).toString();
    }

    @Tool(name = "getMarkdownOutline", description = "Returns the heading structure (table of contents) of a Markdown file with line numbers and section sizes. Use this to understand a large Markdown document before fetching specific sections with getMarkdownSection.", type = "object")
    public String getMarkdownOutline(
            @ToolParam(name = "projectName", description = "The name of the project containing the Markdown file", required = true) String projectName,
            @ToolParam(name = "resourcePath", description = "The path to the Markdown file relative to the project root (e.g., 'docs/README.md')", required = true) String resourcePath)
    {
        return markdownService.getOutline(projectName, resourcePath);
    }

    @Tool(name = "getMarkdownSection", description = "Reads a specific section from a Markdown file by heading name or index. Returns the section content with line numbers. Use getMarkdownOutline first to see available headings.", type = "object")
    public String getMarkdownSection(
            @ToolParam(name = "projectName", description = "The name of the project containing the Markdown file", required = true) String projectName,
            @ToolParam(name = "resourcePath", description = "The path to the Markdown file relative to the project root", required = true) String resourcePath,
            @ToolParam(name = "heading", description = "The heading to find â€” either a 1-based index from the outline, or a text substring to match (case-insensitive)", required = true) String heading,
            @ToolParam(name = "includeSubsections", description = "If 'true', includes all subsections under the matched heading. If 'false', returns only the content up to the next heading of any level. Default: true", required = false) String includeSubsections)
    {
        boolean includeSubs = Optional.ofNullable(includeSubsections).map(Boolean::parseBoolean).orElse(true);
        return markdownService.getSection(projectName, resourcePath, heading, includeSubs);
    }

    // --- Launch & Debug Tools (formerly eclipse-runner) ---

    @Tool(name = "runJavaApplication",
          description = "Launches a Java application in run mode. Specify the project and fully qualified main class. If timeout > 0, waits for the process to finish and returns stdout/stderr. If timeout = 0, launches in background and returns immediately.",
          type = "object")
    public String runJavaApplication(
            @ToolParam(name = "projectName", description = "The name of the project containing the main class") String projectName,
            @ToolParam(name = "mainClass", description = "The fully qualified name of the main class (e.g., 'com.example.Main')") String mainClass,
            @ToolParam(name = "programArgs", description = "Optional program arguments passed to the main method", required = false) String programArgs,
            @ToolParam(name = "vmArgs", description = "Optional JVM arguments (e.g., '-Xmx512m -Dfoo=bar')", required = false) String vmArgs,
            @ToolParam(name = "timeout", description = "Timeout in seconds to wait for completion. Use '0' to launch in background without waiting. Default: '30'", required = false) String timeout)
    {
        int timeoutSeconds = Optional.ofNullable(timeout).map(Integer::parseInt).orElse(30);
        return javaLaunchService.runJavaApplication(projectName, mainClass, programArgs, vmArgs, timeoutSeconds);
    }

    @Tool(name = "debugJavaApplication",
          description = "Launches a Java application in debug mode. The application will stop at breakpoints. Use toggleBreakpoint to set breakpoints before launching.",
          type = "object")
    public String debugJavaApplication(
            @ToolParam(name = "projectName", description = "The name of the project containing the main class") String projectName,
            @ToolParam(name = "mainClass", description = "The fully qualified name of the main class (e.g., 'com.example.Main')") String mainClass,
            @ToolParam(name = "programArgs", description = "Optional program arguments passed to the main method", required = false) String programArgs,
            @ToolParam(name = "vmArgs", description = "Optional JVM arguments (e.g., '-Xmx512m -Dfoo=bar')", required = false) String vmArgs,
            @ToolParam(name = "timeout", description = "Timeout in seconds to wait for completion. Use '0' to launch in background without waiting. Default: '0'", required = false) String timeout)
    {
        int timeoutSeconds = Optional.ofNullable(timeout).map(Integer::parseInt).orElse(0);
        return javaLaunchService.debugJavaApplication(projectName, mainClass, programArgs, vmArgs, timeoutSeconds);
    }

    @Tool(name = "stopApplication",
          description = "Stops a running or debugging Java application. Matches against the launch configuration name or main class name (substring match, case-insensitive).",
          type = "object")
    public String stopApplication(
            @ToolParam(name = "nameOrClass", description = "A substring to match against the application name or main class (e.g., 'Main' or 'com.example')") String nameOrClass)
    {
        return javaLaunchService.stopApplication(nameOrClass);
    }

    @Tool(name = "listActiveLaunches",
          description = "Lists all currently running or debugging applications with their status, mode (run/debug), and process information.",
          type = "object")
    public String listActiveLaunches()
    {
        return javaLaunchService.listActiveLaunches();
    }

    @Tool(name = "toggleBreakpoint",
          description = "Sets or removes a line breakpoint at the specified location. If a breakpoint already exists at the line, it is removed. Otherwise, a new breakpoint is created.",
          type = "object")
    public String toggleBreakpoint(
            @ToolParam(name = "projectName", description = "The name of the project containing the source file") String projectName,
            @ToolParam(name = "typeName", description = "The fully qualified type name (e.g., 'com.example.Main')") String typeName,
            @ToolParam(name = "lineNumber", description = "The 1-based line number where the breakpoint should be set") String lineNumber)
    {
        return javaLaunchService.toggleBreakpoint(projectName, typeName, Integer.parseInt(lineNumber));
    }

    @Tool(name = "listBreakpoints",
          description = "Lists all breakpoints currently set in the workspace, showing their location, enabled status, and any conditions.",
          type = "object")
    public String listBreakpoints()
    {
        return javaLaunchService.listBreakpoints();
    }

    @Tool(name = "removeAllBreakpoints",
          description = "Removes all breakpoints from the workspace.",
          type = "object")
    public String removeAllBreakpoints()
    {
        return javaLaunchService.removeAllBreakpoints();
    }

    @Tool(name = "getStackTrace",
          description = "Gets the stack trace of all threads for a suspended debug session. Shows the call stack, and local variables for the top frame. The application must be stopped at a breakpoint.",
          type = "object")
    public String getStackTrace(
            @ToolParam(name = "nameOrClass", description = "A substring to match against the debug session name or main class") String nameOrClass)
    {
        return javaLaunchService.getStackTrace(nameOrClass);
    }

    @Tool(name = "resumeDebug",
          description = "Resumes execution of a suspended debug session. The application will continue running until it hits the next breakpoint or terminates.",
          type = "object")
    public String resumeDebug(
            @ToolParam(name = "nameOrClass", description = "A substring to match against the debug session name or main class") String nameOrClass)
    {
        return javaLaunchService.resumeDebug(nameOrClass);
    }

    @Tool(name = "stepOver",
          description = "Steps over the current line in a suspended debug session. Executes the current line without entering method calls.",
          type = "object")
    public String stepOver(
            @ToolParam(name = "nameOrClass", description = "A substring to match against the debug session name or main class") String nameOrClass)
    {
        return javaLaunchService.stepOver(nameOrClass);
    }

    @Tool(name = "stepInto",
          description = "Steps into the method call at the current line in a suspended debug session. Enters the called method.",
          type = "object")
    public String stepInto(
            @ToolParam(name = "nameOrClass", description = "A substring to match against the debug session name or main class") String nameOrClass)
    {
        return javaLaunchService.stepInto(nameOrClass);
    }

    @Tool(name = "stepReturn",
          description = "Steps out of the current method in a suspended debug session. Runs until the current method returns to its caller.",
          type = "object")
    public String stepReturn(
            @ToolParam(name = "nameOrClass", description = "A substring to match against the debug session name or main class") String nameOrClass)
    {
        return javaLaunchService.stepReturn(nameOrClass);
    }

    @Tool(name = "evaluateExpression",
          description = "Evaluates a Java expression in the context of a suspended debug frame. The application must be stopped at a breakpoint. Can evaluate any valid Java expression including method calls, field access, arithmetic, etc.",
          type = "object")
    public String evaluateExpression(
            @ToolParam(name = "nameOrClass", description = "A substring to match against the debug session name or main class") String nameOrClass,
            @ToolParam(name = "expression", description = "The Java expression to evaluate (e.g., 'myList.size()', 'x + y', 'this.toString()')") String expression)
    {
        return javaLaunchService.evaluateExpression(nameOrClass, expression);
    }

    @Tool(name = "setConditionalBreakpoint",
          description = "Sets a breakpoint with a condition expression. The breakpoint only triggers when the condition evaluates to true. Replaces any existing breakpoint at the same location.",
          type = "object")
    public String setConditionalBreakpoint(
            @ToolParam(name = "projectName", description = "The name of the project containing the source file") String projectName,
            @ToolParam(name = "typeName", description = "The fully qualified type name (e.g., 'com.example.Main')") String typeName,
            @ToolParam(name = "lineNumber", description = "The 1-based line number where the breakpoint should be set") String lineNumber,
            @ToolParam(name = "condition", description = "A Java boolean expression (e.g., 'i > 100', 'name.equals(\"test\")')") String condition,
            @ToolParam(name = "hitCount", description = "Optional: breakpoint triggers only after being hit N times. Default: '0' (disabled)", required = false) String hitCount)
    {
        int hitCountInt = Optional.ofNullable(hitCount).map(Integer::parseInt).orElse(0);
        return javaLaunchService.setConditionalBreakpoint(projectName, typeName,
                Integer.parseInt(lineNumber), condition, hitCountInt);
    }

    @Tool(name = "hotCodeReplace",
          description = "Triggers hot code replace (HCR) in an active debug session. Compiles the latest code changes and pushes them into the running JVM without restarting the application. The JVM must support HCR (most standard JVMs do).",
          type = "object")
    public String hotCodeReplace(
            @ToolParam(name = "nameOrClass", description = "A substring to match against the debug session name or main class") String nameOrClass)
    {
        return javaLaunchService.hotCodeReplace(nameOrClass);
    }

    // --- Workspace Context & History Tools (formerly eclipse-context) ---

    @Tool(name = "listCachedResources",
          description = "Lists all resources currently cached in the Eclipse workspace context. "
                      + "Shows URIs, types, version numbers, timestamps, and token estimates. "
                      + "Use this to see what files, classes, and data the user has been working with.",
          type = "object")
    public String listCachedResources()
    {
        Map<URI, CachedResource> all = resourceCache.getAll();
        if ( all.isEmpty() )
        {
            return "No resources cached. Use eclipse-ide tools (getSource, readProjectResource, "
                 + "getCurrentlyOpenedFile, etc.) to load resources into the cache.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append( "# Cached Resources\n\n" );
        sb.append( resourceCache.getStats() ).append( "\n\n" );
        sb.append( String.format( "%-50s  %-15s  %-5s  %-20s  %s\n",
                "URI", "Type", "Ver", "Cached At", "Tokens" ) );
        sb.append( "-".repeat( 110 ) ).append( "\n" );

        for ( var entry : all.entrySet() )
        {
            CachedResource r = entry.getValue();
            String uri = truncate( entry.getKey().toString(), 50 );
            sb.append( String.format( "%-50s  %-15s  v%-4d  %-20s  ~%d\n",
                    uri,
                    r.descriptor().type(),
                    r.version(),
                    TIMESTAMP_FMT.format( r.cachedAt() ),
                    r.estimateTokens() ) );
        }

        return sb.toString();
    }

    @Tool(name = "getCachedResource",
          description = "Gets the content of a specific cached resource by URI without re-reading from disk. "
                      + "Use listCachedResources first to see available URIs. "
                      + "Returns the cached version â€” fast, no I/O.",
          type = "object")
    public String getCachedResource(
            @ToolParam(name = "resourceUri",
                       description = "The URI of the cached resource (e.g. 'workspace:///ProjectName/src/File.java' or 'jdt:///com.example.MyClass')",
                       required = true) String resourceUri )
    {
        try
        {
            URI uri = URI.create( resourceUri );
            return resourceCache.get( uri )
                    .map( r -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append( "# " ).append( r.descriptor().displayName() )
                          .append( " (v" ).append( r.version() )
                          .append( ", cached " ).append( TIMESTAMP_FMT.format( r.cachedAt() ) )
                          .append( ")\n\n" );
                        sb.append( r.content() );
                        return sb.toString();
                    } )
                    .orElse( "Resource not found in cache: " + resourceUri
                           + "\nUse listCachedResources to see available URIs." );
        }
        catch ( Exception e )
        {
            return "Invalid URI: " + resourceUri + " â€” " + e.getMessage();
        }
    }

    @Tool(name = "getCacheStats",
          description = "Gets resource cache statistics: number of resources, token usage, and limits.",
          type = "object")
    public String getCacheStats()
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "# Resource Cache Statistics\n\n" );
        sb.append( resourceCache.getStats() ).append( "\n\n" );

        if ( !resourceCache.isEmpty() )
        {
            sb.append( "## Summary\n" );
            sb.append( resourceCache.toSummary() );
        }

        return sb.toString();
    }

    @Tool(name = "getFileHistory",
          description = "Lists the Local History versions of a file maintained by Eclipse. "
                      + "Shows timestamps and sizes for each historical version. "
                      + "Eclipse automatically saves file history on every modification through the IDE.",
          type = "object")
    public String getFileHistory(
            @ToolParam(name = "projectName", description = "The name of the project", required = true) String projectName,
            @ToolParam(name = "filePath", description = "Path to the file relative to the project root", required = true) String filePath,
            @ToolParam(name = "maxEntries", description = "Maximum number of history entries to show (default: 20)", required = false) String maxEntries )
    {
        return localHistoryService.getFileHistory( projectName, filePath, maxEntries );
    }

    @Tool(name = "getFileHistoryContent",
          description = "Gets the content of a specific Local History version of a file. "
                      + "Use getFileHistory first to see available versions and their indices.",
          type = "object")
    public String getFileHistoryContent(
            @ToolParam(name = "projectName", description = "The name of the project", required = true) String projectName,
            @ToolParam(name = "filePath", description = "Path to the file relative to the project root", required = true) String filePath,
            @ToolParam(name = "index", description = "The history index (0 = most recent, from getFileHistory)", required = true) String index )
    {
        return localHistoryService.getFileHistoryContent( projectName, filePath, index );
    }

    @Tool(name = "restoreFileVersion",
          description = "Restores a file to a specific Local History version. "
                      + "The current content becomes a new history entry before the restore. "
                      + "Use getFileHistory to find the version index.",
          type = "object")
    public String restoreFileVersion(
            @ToolParam(name = "projectName", description = "The name of the project", required = true) String projectName,
            @ToolParam(name = "filePath", description = "Path to the file relative to the project root", required = true) String filePath,
            @ToolParam(name = "index", description = "The history index to restore (0 = most recent, from getFileHistory)", required = true) String index )
    {
        return localHistoryService.restoreFileVersion( projectName, filePath, index );
    }

    @Tool(name = "compareWithHistory",
          description = "Shows a unified diff between the current file content and a Local History version. "
                      + "Use getFileHistory to find the version index.",
          type = "object")
    public String compareWithHistory(
            @ToolParam(name = "projectName", description = "The name of the project", required = true) String projectName,
            @ToolParam(name = "filePath", description = "Path to the file relative to the project root", required = true) String filePath,
            @ToolParam(name = "index", description = "The history index to compare against (0 = most recent, from getFileHistory)", required = true) String index )
    {
        return localHistoryService.compareWithHistory( projectName, filePath, index );
    }

    // --- Git Tools (formerly eclipse-git) ---

    @Tool(name = "gitStatus", description = "Shows the working tree status of the Git repository associated with the project. Displays staged, unstaged, untracked files and current branch info.", type = "object")
    public String gitStatus(
            @ToolParam(name = "projectName", description = "The Eclipse project name (use listProjects to find it)", required = true) String projectName)
    {
        return gitService.getStatus(projectName);
    }

    @Tool(name = "gitLog", description = "Shows the commit history of the Git repository associated with the project.", type = "object")
    public String gitLog(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "maxCount", description = "Maximum number of commits to show (default: 20)", required = false) String maxCount)
    {
        int count = Optional.ofNullable(maxCount).map(Integer::parseInt).orElse(20);
        return gitService.getLog(projectName, count);
    }

    @Tool(name = "gitAdd", description = "Stages files for the next commit. Use '.' to stage all changes (new, modified, and deleted files).", type = "object")
    public String gitAdd(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "filePattern", description = "File pattern to add (e.g., '.' for all, 'src/com/example/MyClass.java' for a specific file)", required = true) String filePattern)
    {
        return gitService.addFiles(projectName, filePattern);
    }

    @Tool(name = "gitCommit", description = "Commits the currently staged changes with the given message.", type = "object")
    public String gitCommit(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "message", description = "The commit message", required = true) String message)
    {
        return gitService.commit(projectName, message);
    }

    @Tool(name = "gitDiff", description = "Shows the diff of changes in unified diff format. By default shows unstaged (working tree) changes; set staged to 'true' to see staged changes.", type = "object")
    public String gitDiff(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "staged", description = "If 'true', shows staged (cached) changes instead of unstaged. Default: false", required = false) String staged)
    {
        boolean isStagedDiff = Optional.ofNullable(staged).map(Boolean::parseBoolean).orElse(false);
        return gitService.getDiff(projectName, isStagedDiff);
    }

    @Tool(name = "gitBranch", description = "Lists branches in the repository. The current branch is marked with an asterisk (*).", type = "object")
    public String gitBranch(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "includeRemote", description = "If 'true', includes remote-tracking branches. Default: false", required = false) String includeRemote)
    {
        boolean remote = Optional.ofNullable(includeRemote).map(Boolean::parseBoolean).orElse(false);
        return gitService.listBranches(projectName, remote);
    }

    @Tool(name = "gitCreateBranch", description = "Creates a new branch. Does not switch to it â€” use gitCheckout to switch.", type = "object")
    public String gitCreateBranch(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "branchName", description = "Name of the new branch to create", required = true) String branchName,
            @ToolParam(name = "startPoint", description = "Optional start point (branch name, tag, or commit SHA). Defaults to HEAD.", required = false) String startPoint)
    {
        return gitService.createBranch(projectName, branchName, startPoint);
    }

    @Tool(name = "gitDeleteBranch", description = "Deletes a branch. Cannot delete the currently checked-out branch.", type = "object")
    public String gitDeleteBranch(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "branchName", description = "Name of the branch to delete", required = true) String branchName,
            @ToolParam(name = "force", description = "If 'true', force-deletes even if the branch is not fully merged. Default: false", required = false) String force)
    {
        boolean forceDelete = Optional.ofNullable(force).map(Boolean::parseBoolean).orElse(false);
        return gitService.deleteBranch(projectName, branchName, forceDelete);
    }

    @Tool(name = "gitCheckout", description = "Checks out a branch, switching the working tree to that branch.", type = "object")
    public String gitCheckout(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "branchName", description = "The branch name to checkout", required = true) String branchName)
    {
        return gitService.checkoutBranch(projectName, branchName);
    }

    @Tool(name = "gitReset", description = "Unstages files from the index (equivalent to 'git reset HEAD <file>'). Does not modify the working tree.", type = "object")
    public String gitReset(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "filePattern", description = "File pattern to unstage (e.g., '.' for all, or a specific file path)", required = true) String filePattern)
    {
        return gitService.resetFiles(projectName, filePattern);
    }

    @Tool(name = "gitStash", description = "Stashes the current working directory and index changes, reverting the working tree to HEAD.", type = "object")
    public String gitStash(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "message", description = "Optional message to describe the stash", required = false) String message)
    {
        return gitService.stash(projectName, message);
    }

    @Tool(name = "gitStashPop", description = "Applies and removes the most recent stash entry, restoring previously stashed changes.", type = "object")
    public String gitStashPop(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName)
    {
        return gitService.stashPop(projectName);
    }

    @Tool(name = "gitStashList", description = "Lists all stash entries.", type = "object")
    public String gitStashList(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName)
    {
        return gitService.stashList(projectName);
    }

    // --- PDE Tools (formerly eclipse-pde) ---

    @Tool(name = "getActiveTarget",
          description = "Gets information about the currently active Eclipse target platform.",
          type = "object")
    public String getActiveTarget()
    {
        return pdeService.getActiveTarget();
    }

    @Tool(name = "setActiveTarget",
          description = "Sets the active Eclipse target platform from a .target file. Loads and activates the target definition.",
          type = "object")
    public String setActiveTarget(
            @ToolParam(name = "targetFilePath", description = "The workspace-relative or absolute path to the .target file (e.g., '/MyProject/myplatform.target')") String targetFilePath)
    {
        return pdeService.setActiveTarget(targetFilePath);
    }

    @Tool(name = "reloadTarget",
          description = "Reloads the currently active Eclipse target platform. Useful after target contents change on disk.",
          type = "object")
    public String reloadTarget()
    {
        return pdeService.reloadTarget();
    }

    @Tool(name = "runJUnitPluginTests",
          description = "Runs all JUnit Plug-in Tests in the specified project using the PDE launcher. Returns test results including pass/fail counts.",
          type = "object")
    public String runJUnitPluginTests(
            @ToolParam(name = "projectName", description = "The exact Eclipse project name containing the plug-in test classes") String projectName,
            @ToolParam(name = "timeout", description = "Maximum time in seconds to wait for test completion (default: 60)", required = false) String timeout)
    {
        int timeoutSeconds = Optional.ofNullable(timeout).map(Integer::parseInt).orElse(60);
        return pdeService.runJUnitPluginTests(projectName, timeoutSeconds);
    }

    @Tool(name = "runJUnitPluginTestClass",
          description = "Runs all JUnit Plug-in Tests in a specific class using the PDE launcher. Returns test results.",
          type = "object")
    public String runJUnitPluginTestClass(
            @ToolParam(name = "projectName", description = "The exact Eclipse project name containing the test class") String projectName,
            @ToolParam(name = "className", description = "The fully qualified class name (e.g., 'com.example.MyPluginTest')") String className,
            @ToolParam(name = "timeout", description = "Maximum time in seconds to wait for test completion (default: 60)", required = false) String timeout)
    {
        int timeoutSeconds = Optional.ofNullable(timeout).map(Integer::parseInt).orElse(60);
        return pdeService.runJUnitPluginTestClass(projectName, className, timeoutSeconds);
    }

    // --- Helpers ---

    private static String[] normalizeFileNamePatterns(Object fileNamePatterns)
    {
        if (fileNamePatterns == null)
        {
            return new String[0];
        }

        if (fileNamePatterns instanceof String[])
        {
            return (String[]) fileNamePatterns;
        }

        if (fileNamePatterns instanceof List)
        {
            @SuppressWarnings("rawtypes")
            List list = (List) fileNamePatterns;
            List<String> out = new ArrayList<>();
            for (Object o : list)
            {
                if (o != null)
                {
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty())
                    {
                        out.add(s);
                    }
                }
            }
            return out.toArray(String[]::new);
        }

        if (fileNamePatterns instanceof String)
        {
            String s = ((String) fileNamePatterns).trim();
            if (s.isEmpty())
            {
                return new String[0];
            }

            return s.split("\\s*,\\s*");
        }

        String s = String.valueOf(fileNamePatterns).trim();
        return s.isEmpty() ? new String[0] : new String[] { s };
    }

    private static String truncate( String s, int maxLen )
    {
        if ( s.length() <= maxLen ) return s;
        return "..." + s.substring( s.length() - maxLen + 3 );
    }
}
