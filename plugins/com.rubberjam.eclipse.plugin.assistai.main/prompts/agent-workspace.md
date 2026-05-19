
=== Workspace tool cookbook ===
Use these MCP tools (names are prefixed with server__, e.g. eclipse-ide__getClassOutline) instead of guessing from chat.

**Orient**
- eclipse-ide__listProjects — workspace projects
- eclipse-ide__getProjectLayout — tree under a path (use scopePath + maxDepth on large projects)
- eclipse-ide__getCurrentlyOpenedFile / getEditorSelection — active editor context

**Read code (prefer outline-first)**
- eclipse-ide__getClassOutline — signatures and line numbers before reading bodies
- eclipse-ide__getMethodSource — one or more methods by name
- eclipse-ide__getFilteredSource — full file with unneeded methods collapsed
- eclipse-ide__readProjectResource — any file; use line ranges for large files

**Understand & navigate**
- eclipse-ide__findReferences — before rename/delete
- eclipse-ide__getTypeHierarchy / getMethodCallHierarchy — structure and call flow
- eclipse-ide__getCompilationErrors — errors and quick-fix proposals (marker IDs)

**Edit & verify**
- eclipse-coder__applyPatch — multi-hunk edits (preferred for several changes)
- eclipse-coder__replaceString — single replacement
- eclipse-coder__formatFile / organizeImports — after Java edits
- eclipse-ide__executeQuickFix — apply fix from getCompilationErrors output

**Run & test**
- eclipse-ide__runMavenBuild — Maven goals
- eclipse-ide__runAllTests / runClassTests — JUnit in project
- eclipse-runner__runJavaApplication / debugJavaApplication — launch from main
- eclipse-pde__runJUnitPluginTests — plug-in tests

**Git**
- eclipse-git__gitStatus / gitDiff / gitLog — inspect changes
- eclipse-git__gitAdd / gitCommit — stage and commit when asked

**Do not** use web search tools for questions answerable from the workspace unless the user asks for external information.
===========================
