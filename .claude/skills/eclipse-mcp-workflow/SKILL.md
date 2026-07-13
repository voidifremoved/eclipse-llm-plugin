---
name: eclipse-mcp-workflow
description: Use Eclipse MCP endpoints effectively for Java and Eclipse PDE work. Use when an agent needs to inspect, edit, build, test, run, debug, refactor, or operate on Eclipse workspace projects through MCP tools.
argument-hint: [task involving an Eclipse workspace]
---

# Eclipse MCP Workflow

Prefer Eclipse MCP endpoints for Java/Eclipse workspace work because they use Eclipse's live model: JDT, PDE, refactoring, launch configs, console output, local history, and incremental compilation.

## First Rule

Before calling an MCP endpoint through `CallMcpTool`, read its JSON descriptor from:

`/Users/dhewitt/.cursor/projects/Users-dhewitt-git-eclipse-llm-plugin/mcps/user-eclipse-MCP/tools/<tool-name>.json`

Use the descriptor's parameter names and types exactly. Do this even when the tool name looks obvious.

If direct typed MCP tools such as `mcp__eclipse-ide__getClassOutline` are available in the agent environment, use them directly. If only generic MCP access is available, use `CallMcpTool` with server `user-eclipse-MCP`.

## Endpoint Families

- Analysis: `listProjects`, `getProjectLayout`, `getProjectProperties`, `getClassOutline`, `getMethodSource`, `getFilteredSource`, `getSource`, `readProjectResource`, `findReferences`, `getTypeHierarchy`, `getMethodCallHierarchy`, `getJavaDoc`, `fileSearch`, `fileSearchRegExp`, `findFiles`.
- Editing: `applyPatch`, `replaceString`, `insertIntoFile`, `replaceFileContent`, `createFile`, `deleteFile`, `deleteLinesInFile`, `createDirectories`, `renameFile`, `moveResource`, `undoEdit`, `formatFile`, `organizeImports`, `organizeImportsInPackage`.
- Refactoring: `refactorRenameJavaType`, `refactorMoveJavaType`, `refactorRenamePackage`, `refactorRenameJavaField`, `refactorRenameJavaMethod`.
- Build and test: `getCompilationErrors`, `runAllTests`, `runPackageTests`, `runClassTests`, `runTestMethod`, `findTestClasses`, `runMavenBuild`, `listMavenProjects`, `getEffectivePom`, `getProjectDependencies`, `updateMavenProject`.
- Run and debug: `runJavaApplication`, `debugJavaApplication`, `stopApplication`, `listActiveLaunches`, `getConsoleOutput`, `toggleBreakpoint`, `setConditionalBreakpoint`, `listBreakpoints`, `removeAllBreakpoints`, `getStackTrace`, `evaluateExpression`, `stepOver`, `stepInto`, `stepReturn`, `resumeDebug`, `hotCodeReplace`.
- PDE and target platform: `getActiveTarget`, `setActiveTarget`, `reloadTarget`, `runJUnitPluginTests`, `runJUnitPluginTestClass`.
- Git/local history helpers: `gitStatus`, `gitDiff`, `gitLog`, `gitAdd`, `gitCommit`, `gitBranch`, `gitCheckout`, `gitReset`, `gitStash`, `gitStashPop`, `gitStashList`, `compareWithHistory`, `getFileHistory`, `getFileHistoryContent`, `restoreFileVersion`.

## Standard Workflow

1. Orient with `listProjects`, `getProjectLayout`, or `getCurrentlyOpenedFile` when the project/file is unclear.
2. Read efficiently before editing:
   - Use `getClassOutline` for Java structure.
   - Use `getMethodSource` for 1-3 methods.
   - Use `getFilteredSource` when you need class context without every method body.
   - Use `readProjectResource` for non-Java files or specific line ranges.
3. Analyze with JDT before risky changes: `findReferences`, `getTypeHierarchy`, `getMethodCallHierarchy`, and `getCompilationErrors`.
4. Edit with Eclipse-aware tools:
   - Use `applyPatch` for multi-hunk edits.
   - Use `replaceString` for one exact replacement.
   - Use refactoring endpoints for Java type, method, field, or package renames.
5. Clean up with `organizeImports` and `formatFile` when Java code changed.
6. Verify with `getCompilationErrors` first, then focused tests or builds.

## Editing Guidance

Always pass `projectName` as the Eclipse project name and `filePath` relative to that project root. Do not include the project name inside `filePath`.

Prefer refactoring endpoints over text edits for symbol moves/renames. Prefer `applyPatch` over multiple `replaceString` calls when a change spans more than one nearby location.

Do not revert user changes. If a file is dirty, read it through Eclipse or the filesystem first and patch only the intended region.

## Testing Guidance

Use the smallest reliable check first:

- For syntax/build errors: `getCompilationErrors`.
- For a changed class with tests: `findTestClasses`, then `runClassTests` or `runTestMethod`.
- For plugin behavior: `runJUnitPluginTestClass` or `runJUnitPluginTests`.
- For Maven-specific issues: `runMavenBuild`, then `getConsoleOutput`.

Report clearly if an MCP call times out or the Eclipse server is unresponsive. Do not repeatedly hammer the same stuck endpoint; fall back to filesystem reads or shell checks if available, and tell the user validation was limited.

## Debug Workflow

For runtime bugs:

1. Set a breakpoint with `toggleBreakpoint` or `setConditionalBreakpoint`.
2. Launch with `debugJavaApplication` and `timeout="0"`.
3. Use `listActiveLaunches` and `getStackTrace` to find the suspended thread.
4. Inspect state with `evaluateExpression`.
5. Step with `stepOver`, `stepInto`, or `stepReturn`.
6. After edits, use `hotCodeReplace` when possible; otherwise restart the launch.
7. Clean up with `stopApplication` and `removeAllBreakpoints`.

## Failure Handling

If an MCP endpoint fails:

- Re-read the descriptor and check required parameters.
- Narrow the request: smaller line ranges, fewer results, or a specific project/file.
- For timeouts, avoid immediate repeated calls to the same endpoint. Try a lower-cost endpoint such as `listProjects` or switch to file/shell inspection.
- If the Eclipse MCP server appears wedged, state that clearly and continue with non-MCP tools when safe.

## Related Skills

Use the focused skills when a task is narrow:

- `eclipse-analyze` for JDT inspection and compiler-aware navigation.
- `eclipse-edit` for Eclipse-synchronized code edits.
- `eclipse-test` for JUnit, PDE, and Maven validation.
- `eclipse-run` for launching applications.
- `eclipse-debug` for breakpoints and interactive debugging.
