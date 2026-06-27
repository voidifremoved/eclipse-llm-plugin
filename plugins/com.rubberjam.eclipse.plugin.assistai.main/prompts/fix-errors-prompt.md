Fix compilation errors in the **currently open file only** (${currentFileName} in project ${currentProjectName}).

Path: ${currentFilePath}

<file>
${currentFileContent}
</file>

<selection>
${selectedContent}
</selection>

<errors in this file only>
${errors}
</errors>

Instructions:
- Use eclipse-ide__getCompilationErrors with projectName=${currentProjectName} and filePath=${currentFilePath}.
- Fix problems only in this file using eclipse-ide__executeQuickFix and/or eclipse-coder__applyPatch (or replaceString).
- Re-run getCompilationErrors with the same filePath until this file is clean or you are blocked.
- Do not fix other files, change the build, or run Maven unless the user explicitly asked.
