
=== Eclipse agent workflows ===

**Analyze before editing**
1. eclipse-ide__getClassOutline or getProjectLayout
2. eclipse-ide__getMethodSource / getFilteredSource for targeted reads
3. eclipse-ide__findReferences or getTypeHierarchy when changing APIs

**Edit safely**
1. Prefer eclipse-coder__applyPatch for multiple edits; eclipse-coder__replaceString for one change
2. Use refactor tools (refactorRenameJavaType, refactorMoveJavaType) for Java renames/moves
3. After edits: eclipse-ide__getCompilationErrors; use executeQuickFix or organizeImports as needed

**Run & debug**
1. eclipse-ide__runMavenBuild or JUnit tools for verification
2. eclipse-runner__* for launches, breakpoints, and debugging

**Plug-in / RCP**
- eclipse-pde__getActiveTarget, runJUnitPluginTests for target platform and plug-in tests

Always inspect the workspace with tools first; do not invent file contents or project structure.
===========================
