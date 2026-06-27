# AssistAI — Kilocode Parity Implementation Plan

Comprehensive step-by-step roadmap to bring AssistAI to tool-calling and UI feature parity with
[Kilocode](https://github.com/Kilo-Org/kilocode) (a VS Code agentic IDE built on the OpenCode fork).

Analysis based on: Kilocode `packages/opencode/` + `packages/kilo-vscode/` as of May 2026 and
AssistAI main plugin as of May 2026.

---

## Context: What each system already has

### AssistAI strengths (no parity work needed)

| Capability | Detail |
|---|---|
| Deep Eclipse/JDT integration | Type hierarchy, refactoring, quick fixes, call hierarchy, organize imports |
| Debug integration | Breakpoints, step, evaluate, hot code replace |
| Maven integration | Goals, effective POM, dependencies |
| PDE / OSGi | Target platform, plugin JUnit tests |
| Git via JGit | Status, diff, commit, branch, stash |
| Local history / undo | Per-file undo backed by Eclipse local history |
| Dual MCP role | Consumes tools AND exposes them over HTTP for external agents |
| Multi-tab sessions | Persistent tabs with per-tab model, mode, and draft |
| ASK / AGENT / PLAN modes | Three interaction modes already implemented |
| Post-edit compile verify | Auto `getCompilationErrors` after coder edits |
| Code completion | `Alt+/` in-editor ghost text completion |

### Kilocode capabilities that AssistAI is missing

Grouped into eight implementation phases ordered by impact.

---

## Phase 1 — Shell / Bash Tool

**Priority: Critical.** Kilocode's most-used tool is `bash`. Without it the agent cannot run
arbitrary commands (npm, docker, make, scripts, git commands beyond JGit, etc.). Every other
agentic IDE considers a shell tool table-stakes.

### 1.1 Create `ShellService`

New class: `mcp/services/ShellService.java`

- `runCommand(String command, String workingDirectory, int timeoutSeconds)` → `ShellResult`
- `ShellResult`: exit code (int), stdout+stderr combined (String), truncated flag (boolean),
  elapsed ms (long).
- Use `ProcessBuilder` with `redirectErrorStream(true)`.
- Cap output at 50 KB; append `\n[output truncated]` if exceeded.
- Enforce timeout via `process.waitFor(timeout, SECONDS)`; destroy process on timeout.
- Resolve working directory: if blank, use active project root from `PromptContextValueProvider`;
  fall back to workspace root.
- Track current working directory in a per-session `Map<String, Path>` (keyed by tab ID).
  Update it when the agent runs `cd`.
- Platform detection: `System.getProperty("os.name")`. On Windows default to
  `cmd.exe /c`; expose a preference to override with PowerShell or Git Bash.

### 1.2 Create `ShellMcpServer`

New class: `mcp/servers/ShellMcpServer.java`

Tools exposed:

| Tool | Parameters | Description |
|---|---|---|
| `runShell` | `command` (required), `workingDirectory` (optional), `timeoutSeconds` (optional, default 30) | Run an arbitrary shell command and return its output |
| `getShellCwd` | (none) | Return the current working directory for this session |

Register in `McpServerBuiltins` as server name `eclipse-shell`, tier `AgentToolTier.SHELL` (new tier).

### 1.3 Add `AgentToolTier.SHELL`

In `AgentToolTier.java`, add `SHELL` between `WEB` and `USER`. In `AgentToolPolicy`:

- `SHELL` defaults to **requires per-call confirmation** (not auto-approved).
- Add preference `ASSISTAI_AGENT_ALLOW_SHELL_TOOLS` (default false).
- When true, skip confirmation for shell tools (equivalent to Kilocode's auto-approve for bash).

### 1.4 Approval gate (interim)

Before Phase 2 delivers the permission dock, use a blocking `MessageDialog.openConfirm` on the
UI thread showing the command and working directory. The `McpToolInvocationExecutor` already
runs on the UI thread — add the dialog there for `SHELL` tier tools.

### 1.5 Stream output to chat

Rather than waiting for full process completion, publish incremental `AgentStreamChunk` events
with type `TOOL_OUTPUT` as lines arrive. The chat `Browser` appends these to the active tool
call row in real time (prefix lines with a monospace `> ` style). Final chunk includes exit code.

### 1.6 System prompt update

Add a section to `agent-system-prompt.md` documenting shell tool usage, safety guidance
(prefer workspace-relative paths, avoid destructive operations without confirming first), and
the current shell / OS injected via `AgentSystemPromptBuilder`.

---

## Phase 2 — Per-Call Permission Dock

**Priority: Critical.** Kilocode shows a docked approval widget above the prompt for every tool
call that needs confirmation — command text or diff, per-rule "always allow/deny" toggles, and
Deny / Run buttons. Currently AssistAI runs most tools silently or only shows a dialog for the
apply-patch wizard.

### 2.1 Data model

New class: `agent/ToolApprovalRequest.java`

Fields: `tabId` (String), `toolName` (String), `serverName` (String), `parametersSummary`
(String — rendered from tool parameters), `diff` (String, nullable — unified diff for edit
tools), `filePath` (String, nullable), `future` (CompletableFuture&lt;ToolApprovalDecision&gt;).

`ToolApprovalDecision`: enum `ALLOW_ONCE`, `ALLOW_ALWAYS`, `DENY`.

### 2.2 Pause the agent engine

In `McpToolInvocationExecutor` (or `McpToolBridge`), before executing any tool whose tier
requires confirmation, publish a `ToolApprovalRequest` via `IEventBroker` on topic
`AssistAI/tool/approval/request`. Block the Reactor scheduler thread on
`request.future().get(5, MINUTES)`. On timeout treat as DENY.

### 2.3 Permission dock composite

In `ChatView`, add a `PermissionDockComposite` (a `Composite` with `GridLayout`) between the
message area and the prompt input. Hide it when no pending request exists for the active tab.

Layout:
```
┌─────────────────────────────────────────────────────────┐
│ 🔧 eclipse-shell__runShell                              │
│ Command: git status                                      │
│ Working dir: /workspace/myproject                        │
│                        [☐ Always allow]  [Deny] [Run ▶] │
└─────────────────────────────────────────────────────────┘
```

For file-edit tools, add a collapsible diff section (see Phase 3).

Components:
- Tool name label with provider icon
- Command / parameters summary (`StyledText`, read-only, monospace)
- "Always allow this pattern" checkbox (pre-populated from persisted rules)
- **Deny** button → resolves future with `DENY`
- **Run** button → resolves future with `ALLOW_ONCE` or `ALLOW_ALWAYS` based on checkbox

### 2.4 Per-pattern always-allow rules

Persist approved patterns to `IPreferenceStore` as JSON:
`{ "eclipse-shell": ["git *", "mvn *"], "eclipse-coder": ["*"] }`.

On incoming request, check persisted rules — if the tool+pattern matches an always-allow rule,
skip the dock entirely and auto-approve. If it matches an always-deny rule, auto-deny.

On "Run" with checkbox checked: serialize the pattern (e.g., `git *` for `git status`) and
save to preferences.

### 2.5 Auto-approve settings

Add an **Agent Approval** section to the existing MCP preference page:

| Setting | Default | Description |
|---|---|---|
| Auto-approve file edits | false | Skip dock for `eclipse-coder` tools |
| Auto-approve read-only tools | true | Skip dock for tools marked read-only |
| Auto-approve shell tools | false | Skip dock for `eclipse-shell` tools |
| Show approval dock for web tools | true | Require confirmation for search/fetch |

### 2.6 Tool cancellation

When the user clicks **Deny**, publish a cancellation event. The agent loop (in
`AgentChatSession`) catches `ToolApprovalDecision.DENY` and injects a synthetic tool result:
`"Tool execution was denied by the user."` so the model can respond gracefully.

---

## Phase 3 — Inline Diff Display for File Edits

**Priority: High.** When the agent edits a file, the user currently just sees confirmation text
in chat. Kilocode shows a syntax-highlighted before/after diff inline in the message, with an
option to open in Eclipse's Compare editor.

### 3.1 Compute diffs in `CodeEditingService`

After every `replaceString`, `insertIntoFile`, `applyPatch`, `replaceFileContent`, and
`deleteLinesInFile`, compute a unified diff using Eclipse's text compare API
(`RangeDifference` / `DocLineComparator`). Return it in `ResourceToolResult` as an additional
`diff` field.

### 3.2 Render diffs in chat

`AgentToolCallFormatter` already produces HTML for tool call rows. Extend it: when a tool
result contains a `diff` field, append a collapsible `<details>` block:

```html
<details class="diff-block">
  <summary>3 additions, 1 deletion — MyClass.java</summary>
  <pre class="diff">
    <span class="ctx"> public void foo() {</span>
    <span class="del">-    int x = 1;</span>
    <span class="add">+    int x = 42;</span>
    <span class="ctx"> }</span>
  </pre>
  <a href="#" onclick="openInCompare('projectName', 'filePath')">Open in Compare</a>
  <a href="#" onclick="revertEdit('projectName', 'filePath')">Revert</a>
</details>
```

CSS: green background for `+` lines, red background for `-` lines, monospace font, max height
200 px with scroll.

### 3.3 BrowserFunction callbacks

Register two `BrowserFunction`s:

- `openInCompare(projectName, filePath)` — retrieves before/after content from
  `LocalHistoryService` and opens `CompareUI.openCompareDialog`.
- `revertEdit(projectName, filePath)` — calls `codeEditingService.undoEdit(...)` and shows an
  inline notification badge on the tool row.

### 3.4 Permission dock diff preview

In Phase 2's `PermissionDockComposite`, add a collapsible `StyledText` diff preview for
`eclipse-coder` edit tools. Use the same colour scheme as the chat diff. Default collapsed for
multi-hunk patches; expand automatically for small diffs (&lt;20 lines changed).

---

## Phase 4 — Todo Task View and Task Timeline

**Priority: High.** Kilocode surfaces the agent's structured task list as a visual progress
tracker (the `todowrite` tool updates a panel). AssistAI has `AgentTaskItem` /
`AgentTaskChecklistParser` but renders tasks only as plain chat text with no dedicated UI.
The task timeline is a horizontal bar strip showing all tool calls proportional to duration.

### 4.1 Structured todo events

In `AgentChatEngine`, after `AgentTaskChecklistParser` parses a checklist from streamed content,
publish a typed event on topic `AssistAI/agent/todos/updated` carrying a
`List<AgentTaskItem>` and the `tabId`. Add `AgentTaskItem.priority` (HIGH / MEDIUM / LOW) to
match Kilocode's `todowrite` schema.

### 4.2 `TodoView` Eclipse view

New `IViewPart`: `com.rubberjam.eclipse.assistai.TodoView` (id
`com.rubberjam.eclipse.assistai.view.todos`).

- `TableViewer` with columns: **Status** (icon), **Task** (text), **Priority**.
- Status icons: ◯ pending (grey), ⟳ in-progress (blue spinner), ✓ completed (green), ✗
  cancelled (red).
- Subscribe to `AssistAI/agent/todos/updated` and refresh when the active tab's list changes.
- Add to the AssistAI perspective layout (right of chat, 30% height, collapsible).
- Register in `plugin.xml` under `org.eclipse.ui.views` extension point.

### 4.3 Task timeline strip

Add a `Canvas` widget (height 18 px) at the top of the chat message area in `ChatView`, below
the tab bar and above the `Browser`. It is repainted on each `AgentStreamChunk`.

Each tool call from the current session is a coloured segment:
- Width: proportional to elapsed time (normalised to the total session duration so far).
- Colour by type: read-only tool = `#4A9EDB` (blue), coder edit = `#1A6DB5` (dark blue),
  shell = `#E07B39` (orange), web = `#7C5CBF` (purple), error = `#CC3333` (red),
  thinking = `#888888` (grey).
- On mouse hover: `ToolTip` showing tool name, parameters summary, and elapsed time in ms.

Store timeline data in `AgentTabState` as `List<ToolCallTimelineEntry>` (tool name, type,
start ms, end ms).

---

## Phase 5 — Prompt Input Upgrades

### 5.1 Prompt history navigation

Store the last 50 submitted prompts per tab in a `Deque<String>` in `AgentViewPresenter`.
Persist to preferences as a JSON array keyed by tab ID.

On **Up arrow** in the prompt `Text` widget (when cursor is on the first line), load the
previous history entry into the input. On **Down arrow**, advance forward. Escape or any
edit clears the navigation cursor back to the draft.

### 5.2 Slash commands

Extend the existing `ContentProposalAdapter` to fire on `/` as well as `@`.

Built-in commands:

| Command | Action |
|---|---|
| `/clear` | New session in current tab |
| `/ask` | Switch tab to ASK mode |
| `/agent` | Switch tab to AGENT mode |
| `/plan` | Switch tab to PLAN mode |
| `/help` | Insert help text |

User-defined commands: load from `PromptRepository` (already used for context menu handlers).
Show in the content-proposal dropdown with a `/` prefix and the template description.

On selection, substitute `${currentFile}`, `${selectedText}`, `${projectName}` etc. via
`EclipseVariableUtilities` (already used by `PromptContextValueProvider`).

### 5.3 @-style context mentions

Make `@` the primary trigger character for the `ContentProposalAdapter` (it already fires on
any key — restrict to `@` prefix only, alongside `/`).

On `@` typed in the prompt, show workspace files and classes. After selection, insert a styled
chip-like token using `StyleRange` in a `StyledText` (replace the plain `Text` widget with
`StyledText`). The chip renders as `[📄 MyClass.java]` with a distinct background color.

Special built-in mentions (no file picker, keyword match):

| Mention | Injected context |
|---|---|
| `@terminal` | Text of the last active Eclipse Console view (`ConsoleService.getConsoleOutput`) |
| `@git-changes` | Output of `gitDiff()` for the active project |
| `@errors` | Output of `getCompilationErrors()` for the active project |
| `@selection` | Current editor selection text |

These are resolved at send time (not at mention insertion time) so they capture current state.

### 5.4 Replace `Text` with `StyledText` for prompt input

`StyledText` enables:
- `StyleRange` highlighting for `@mention` chips and `/command` tokens.
- Ghost text (see 5.5) via `StyleRange` with grey foreground.
- Spell checking already done by `SpellCheckedTextBox` — integrate there.

### 5.5 Ghost text autocomplete

After the user stops typing for 400 ms (debounce timer), if the prompt is non-empty and the
session is idle, send a short completion request (max 40 tokens, stop at newline) to the
configured model using a lightweight chat message `"Complete this prompt: <text>"`. Display the
suggestion as a `StyleRange` with `SWT.COLOR_DARK_GRAY` foreground immediately after the caret.

**Tab** accepts the ghost text (appends it to the prompt). Any other key clears it.
Add a preference to disable this feature (default off, as it consumes API tokens).

### 5.6 Mode switcher in prompt area

Move the ASK / AGENT / PLAN mode selector from the toolbar `ToolItem` into a compact
`Combo` or flat `ToolBar` inside the prompt `Composite` (bottom-left). Keep the toolbar item
for discoverability but make it synchronise with the in-prompt switcher.

### 5.7 Model selector in prompt area

Add a compact model label/button at the bottom-right of the prompt `Composite` (matching
Kilocode's model chip). Clicking it opens the existing `modelMenu`. The toolbar `ToolItem`
model selector remains as a wider label.

---

## Phase 6 — Provider Expansion

AssistAI supports OpenAI-compatible, Anthropic (native), Google GenAI, Grok, DeepSeek, Groq,
and Mistral. The following providers are missing.

### 6.1 OpenRouter

Register a default `ModelApiDescriptor` template for `https://openrouter.ai/api/v1` with
`apiType = openai`. OpenRouter is OpenAI-compatible. Benefits: access to 200+ models with a
single API key.

Action: add to `PreferenceInitializer` default model list with `apiType = openai`,
`apiUrl = https://openrouter.ai/api/v1`, and instructions in the model description.
No new provider class needed — `OpenAiCompatibleChatModelProvider` handles it.

### 6.2 Ollama (local models)

Add a default `ModelApiDescriptor` template for `http://localhost:11434/v1` with
`apiType = openai`. Ollama exposes an OpenAI-compatible endpoint from v0.1.14+.

Add a preference toggle "Use local Ollama" that pre-fills the base URL and removes the API key
requirement (blank key is valid for Ollama).

### 6.3 Amazon Bedrock

New `ChatModelProvider`: `BedrockChatModelProvider.java`.

- Dependency: Spring AI `spring-ai-bedrock` module (already a known Spring AI provider).
- Additional `ModelApiDescriptor` fields required: `awsRegion`, `awsAccessKeyId`,
  `awsSecretAccessKey` (or credential chain detection via `DefaultCredentialsProvider`).
- Expose a "Use default AWS credential chain" checkbox in `ModelPreferencePage`.

### 6.4 Azure OpenAI

New `AzureChatModelProvider.java` using Spring AI `spring-ai-azure-openai`.

Additional descriptor fields: `azureEndpoint` (full HTTPS URL), `azureApiVersion`.
Route in `ChatModelFactory` when `apiType == "azure"`.

### 6.5 Google Vertex AI

New `VertexAiChatModelProvider.java` using Spring AI `spring-ai-vertex-ai-gemini`.

Additional descriptor fields: `vertexProject`, `vertexLocation`.

### 6.6 GitHub Copilot

New `GitHubCopilotChatModelProvider.java`. GitHub Copilot exposes an OpenAI-compatible
endpoint at `https://api.githubcopilot.com` using a token from the GitHub CLI
(`gh auth token`). Read the token via `Runtime.getRuntime().exec("gh auth token")` or a
user-supplied token field. Route when `apiType == "github-copilot"`.

### 6.7 Thinking / extended reasoning

For Anthropic Claude 3.7+ and OpenAI o-series models, add an optional `thinkingBudget`
field to `ModelApiDescriptor` (token count, 0 = disabled). When set, pass the
`thinking` block in the Anthropic request or `reasoning_effort` for OpenAI. Surface as a
"Thinking budget (tokens)" spinner in `ModelPreferencePage`.

---

## Phase 7 — Session History and Management

### 7.1 Persistent session history panel

New `IViewPart`: `SessionHistoryView` (id
`com.rubberjam.eclipse.assistai.view.sessionhistory`).

- List all past sessions from `.metadata/com.rubberjam.eclipse.assistai/sessions/`.
- Each entry shows: date, model icon, first user message as title, message count.
- Click → open a new tab pre-loaded with the historic conversation.
- Filter text box at the top (search across message content).
- Delete button per entry.

### 7.2 Session persistence format

In `AgentSessionManager`, supplement the existing preferences-based persistence with a
JSON file per session under `.metadata/com.rubberjam.eclipse.assistai/sessions/<uuid>.json`.

Schema:
```json
{
  "id": "uuid",
  "title": "first user message (truncated)",
  "model": "claude-sonnet-4-5",
  "mode": "AGENT",
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601",
  "messages": [ ... ChatMessage list ... ]
}
```

Write on every `AgentSession.addMessage()`. Use Jackson (already a transitive dependency via
Spring AI).

### 7.3 Fork message

In the chat `Browser`, add a right-click context menu on assistant message bubbles:
`BrowserFunction` `showMessageContextMenu(messageIndex)` → native SWT `Menu` with
"Fork from here" item.

Forking creates a new session tab whose message history is a copy of the current tab up to and
including the selected message. The user can then steer the conversation in a different direction
without losing the original.

### 7.4 Export conversation

Add "Export as Markdown" to the tab context menu (`CTabFolder` context menu). Serialize the
conversation to `.md` using `AgentToolCallFormatter` (reuse existing HTML-to-markdown or build
a parallel markdown serializer). Open a `FileDialog` to choose output path.

---

## Phase 8 — Agent Manager (Multi-Session Orchestration)

This is Kilocode's differentiating feature for large-scale agentic work: multiple independent
agent sessions working in parallel, optionally each on an isolated git worktree.

### 8.1 Foundation: the existing tab model

`AgentSessionManager` with `CTabFolder` is already the foundation. The work here is building
orchestration on top.

### 8.2 Git worktree isolation per tab

Add a "New agent in worktree" action (toolbar icon + command
`com.rubberjam.eclipse.assistai.newTabInWorktree`).

When triggered:
1. Prompt for a new branch name via `InputDialog`.
2. Call JGit `AddWorktreeCommand` to create a new worktree at
   `<git-root>/.worktrees/<branch>`.
3. Create a new agent tab bound to that worktree path.
4. The tab's `AgentSystemPromptBuilder` injects the worktree path as the working directory.
5. `ShellService` (Phase 1) and file edit tools default their working directory to the
   worktree path for that tab.

### 8.3 Agent Manager view

New `IViewPart`: `AgentManagerView` (id
`com.rubberjam.eclipse.assistai.view.agentmanager`).

- Shows all active agent tabs as cards in a `ScrolledComposite`:
  - Session name (editable inline)
  - Model + mode badges
  - Status indicator (idle / running / error)
  - Worktree branch (if applicable)
  - Last tool call and elapsed time
  - Message count and approximate token cost
  - **Focus** button → brings that chat tab to foreground
  - **Stop** button → `AssistAIAgentStopHandler` for that tab
- Add to the AssistAI perspective.

### 8.4 Subagent spawning via `task` tool

Add a `task` tool to a new `AgentOrchestrationMcpServer`:

Tool `spawnSubAgent`:
- Parameters: `name` (display name), `prompt` (initial message), `model` (optional, inherits
  parent model if omitted).
- Creates a new agent tab silently, sends the prompt, and returns immediately.
- The parent session receives an update when the subagent finishes (via event bus).
- In the chat view, render a "Subagent" bubble linking to the spawned tab.

### 8.5 Worktree merge back

After a worktree agent tab finishes, show a "Review & Merge" badge on the tab. Clicking it
opens Eclipse's standard **Merge** wizard (`MergeOperation`) with the worktree branch as the
source and the parent branch as the target. Conflicts are surfaced in the usual Eclipse merge
editor.

---

## Phase 9 — Recall / Memory Tool

Kilocode has `kilo_local_recall` for searching past session transcripts. AssistAI has
`MemoryMcpServer` with `think` and `completion_meta` but lacks a recall/search tool.

### 9.1 Session transcript search

In `MemoryMcpServer`, add tool `recallSessions`:
- Parameters: `query` (text to search for), `limit` (max sessions, default 5).
- Scans JSON session files from Phase 7 for messages containing the query.
- Returns matching message excerpts with session title, date, and message index.

### 9.2 Note storage

Add tool `saveNote`:
- Parameters: `content` (text), `tags` (comma-separated, optional).
- Persists to `.metadata/com.rubberjam.eclipse.assistai/notes/<uuid>.md`.
- Prepends YAML frontmatter with date and tags.

Add tool `searchNotes`:
- Parameters: `query`.
- Full-text search across note files.

---

## Phase 10 — MCP Marketplace and UX

Kilocode has a marketplace UI for discovering and installing MCP server configurations.
AssistAI has good MCP support but the setup UX is power-user oriented.

### 10.1 MCP server wizard

Replace the raw "command + env vars" text fields in `McpServerPreferencePage` with a wizard
(`IWizard`) that:
1. Offers "Popular servers" (hardcoded catalogue of ~10 common MCP servers with logos and
   descriptions: filesystem, GitHub, Slack, Postgres, etc.).
2. For each, pre-fills the command and environment variable names with placeholders.
3. Validates connectivity after configuration (calls `ping` or lists tools).

### 10.2 MCP tool browser

In the MCP preference page, after a server is configured and connected, show its discovered
tools in an expandable tree with descriptions. Allow per-tool exclusion via checkboxes
(already persisted in `McpServerDescriptor.excludedTools`).

### 10.3 Remote MCP OAuth

For HTTP MCP servers that support OAuth (e.g. GitHub, Linear), add an OAuth flow:
- "Authenticate" button opens a `BrowserDialog` pointing to the auth URL.
- Register a local callback on a free port (similar to Kilocode's
  `http://127.0.0.1:19876/mcp/oauth/callback`).
- Store the resulting token in Eclipse's `ISecurePreferences`.

---

## Implementation order and rough effort

| Phase | Feature | Effort | Jira-size |
|---|---|---|---|
| 1 | Shell / Bash tool | 3–4 days | M |
| 2 | Permission dock | 4–5 days | L |
| 3 | Inline diff display | 2–3 days | M |
| 4 | Todo view + timeline | 2–3 days | M |
| 5 | Prompt input upgrades | 5–7 days | L |
| 6a | OpenRouter + Ollama | 0.5 days each | XS |
| 6b | Bedrock + Azure + Vertex | 1–2 days each | S |
| 6c | Thinking budget | 1 day | S |
| 7 | Session history + fork | 3–4 days | M |
| 8 | Agent Manager + worktrees | 8–10 days | XL |
| 9 | Recall / memory tool | 2 days | S |
| 10 | MCP marketplace UX | 3–4 days | M |

**Recommended sequence for maximum early impact:**

```
Phase 1 → Phase 2 → Phase 3 → Phase 5 (5.1–5.3) → Phase 4 → Phase 6a → Phase 7 → ...
```

Phases 1–3 together deliver the core agentic experience gap (shell + approval + diff) that
is most visible in day-to-day use. Phase 5 prompts upgrades deliver the UX polish. The
remainder addresses breadth and orchestration.

---

## File locations for each phase

| Phase | New files | Modified files |
|---|---|---|
| 1 | `mcp/services/ShellService.java`, `mcp/servers/ShellMcpServer.java` | `agent/AgentToolTier.java`, `agent/AgentToolPolicy.java`, `mcp/McpServerBuiltins.java`, `springai/McpToolInvocationExecutor.java`, `prompts/agent-system-prompt.md` |
| 2 | `agent/ToolApprovalRequest.java`, `agent/ToolApprovalDecision.java`, `view/PermissionDockComposite.java` | `view/ChatView.java`, `agent/AgentViewPresenter.java`, `springai/McpToolBridge.java`, `preferences/mcp/McpServerPreferencePage.java` |
| 3 | (none) | `mcp/services/CodeEditingService.java`, `mcp/tools/ResourceToolResult.java`, `agent/AgentToolCallFormatter.java`, `view/ChatView.java` (BrowserFunctions) |
| 4 | `view/TodoView.java` | `agent/AgentTaskItem.java`, `agent/AgentTaskChecklistParser.java`, `springai/AgentChatEngine.java`, `agent/AgentTabState.java`, `view/ChatView.java` (timeline canvas), `plugin.xml` |
| 5 | (none) | `view/ChatView.java`, `view/SpellCheckedTextBox.java` → migrate to `StyledText`, `agent/AgentViewPresenter.java` |
| 6 | `springai/provider/BedrockChatModelProvider.java`, `springai/provider/AzureChatModelProvider.java`, `springai/provider/VertexAiChatModelProvider.java`, `springai/provider/GitHubCopilotChatModelProvider.java` | `springai/ChatModelFactory.java`, `preferences/models/ModelPreferencePage.java`, `preferences/PreferenceInitializer.java` |
| 7 | `view/SessionHistoryView.java` | `agent/AgentSessionManager.java`, `agent/AgentSession.java`, `plugin.xml` |
| 8 | `view/AgentManagerView.java`, `mcp/servers/AgentOrchestrationMcpServer.java` | `agent/AgentSessionManager.java`, `view/ChatView.java`, `plugin.xml` |
| 9 | (none) | `mcp/servers/MemoryMcpServer.java`, `mcp/services/` (new note storage) |
| 10 | `preferences/mcp/McpServerWizard.java` | `preferences/mcp/McpServerPreferencePage.java`, `mcp/remote/RemoteMcpClientFactory.java` |
