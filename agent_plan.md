# Tabbed Agent Interface Plan

## Goals

Implement a Cursor-style agent experience in the Eclipse AssistAI chat view:

- Persist agent tabs across Eclipse restarts.
- Support multiple agent tabs running in parallel.
- Store each tab's selected model independently.
- Show tool-call bubbles in the transcript instead of hiding tool activity inside the Spring AI tool loop.

## Current State

- `ChatView` has a `CTabFolder` and can switch between in-memory agent tabs.
- `AgentSessionManager` stores sessions in a runtime-only `Map<String, AgentSession>`.
- `AgentViewPresenter` keeps per-tab stream and attachment state in `AgentTabState`.
- Model selection still comes from `ModelApiDescriptorRepository.getChatModelInUse()`, so model choice is global.
- `AgentSession` keeps history only in memory and appends assistant text after streaming completes.
- Spring AI tools are passed via `ChatClient.defaultToolCallbacks(...)`; tool invocation details are not currently surfaced to the UI.

## Desired Architecture

### Persistent Data Model

Add persisted tab records stored in plugin preferences:

```text
AgentTabDescriptor
- tabId
- title
- modelUid
- active
- messages
```

Only persist serializable chat data:

- user messages
- assistant messages
- tool-call status messages
- text/file attachment metadata where safe

Do not persist transient runtime state:

- active Reactor subscriptions
- in-memory image preview objects
- generated SWT images
- unfinished stream handles

### Session Lifecycle

`AgentSessionManager` should become the source of truth for tab metadata and sessions:

- `loadPersistedTabs()` during view startup.
- `persistTabs()` after tab create, close, title change, active tab change, model change, clear, and completed response.
- `createTab()` creates a tab with a model inherited from the global chat model at creation time.
- `getSelectedModel(tabId)` returns the tab model if present; falls back to global default.
- `setSelectedModel(tabId, modelUid)` only affects that tab.
- `restoreSession(tabDescriptor)` rehydrates `AgentSession` with persisted history and model.

### Per-Tab Model Selection

The model picker should reflect and modify the active tab:

- On tab selection, update the model dropdown to the selected tab's model.
- On model selection, update only that tab's model and switch only that tab's `AgentSession`.
- New tabs default to the global model in preferences at creation time.
- The existing global preference remains the fallback and completion model default.

### Tool-Call Bubbles

Tool call bubbles need an event surface. Spring AI currently executes tool callbacks internally, so introduce an observable wrapper:

- Wrap each `ToolCallback` from `McpToolBridge`.
- Before invocation: emit `TOOL_STARTED` with call id, tool name, and arguments.
- After success: emit `TOOL_FINISHED` with result summary.
- After failure: emit `TOOL_FAILED` with error message.
- `AgentViewPresenter` subscribes to events scoped to the active `AgentSession` / tab.
- `ChatView` renders tool bubbles with a distinct CSS class, collapsible details, and final status.

If Spring AI tool callback APIs make wrapping awkward, start with a minimal tool event adapter around known `ToolCallback` invocation methods using reflection, then replace with typed code once the exact API is verified in Eclipse.

## Implementation Steps

1. Add persisted DTOs:
   - `AgentTabDescriptor`
   - `AgentSessionSnapshot`
   - optional `ToolCallRecord`
   - Status: started with `AgentTabDescriptor` and `AgentMessageSnapshot`.

2. Add preference key:
   - `PreferenceConstants.ASSISTAI_AGENT_TABS`
   - Status: done.

3. Add persistence utilities:
   - serialize/deserialize tab descriptors using Jackson.
   - recover gracefully on malformed JSON.
   - ensure at least one tab exists after load.
   - Status: started in `AgentSessionManager`.

4. Extend `AgentSession`:
   - expose current model uid.
   - initialize from a model uid.
   - import persisted `ChatMessage` history.
   - export serializable history without transient system prompt duplication.
   - Status: mostly done. `AgentSession` now keeps stable UI history separately from Spring AI prompt history, preserves message IDs, exports/imports snapshots, and rebuilds prompt history after deletions.

5. Extend `AgentSessionManager`:
   - load and save persisted tab descriptors.
   - store per-tab model uid.
   - make `switchModel` tab-scoped.
   - persist active tab and tab title changes.
   - Status: started. Runtime sessions now track per-tab model uid and persist descriptors.

6. Update `AgentViewPresenter`:
   - use `sessionManager.getSelectedModel(activeTabId)` in send path.
   - update only active tab on model picker changes.
   - refresh model dropdown on tab selection.
   - persist after message completion, clear, close, and tab title change.
   - Status: mostly done. Send path and model picker are active-tab scoped; completed, stopped, and failed responses now persist.

7. Update `ChatView`:
   - model selector displays active tab model.
   - render persisted messages on startup and tab switch.
   - add APIs for tool bubbles:
     - `appendToolCallMessage`
     - `updateToolCallMessage`
     - `finishToolCallMessage`
   - Status: mostly done with `appendToolCallMessage`, `updateToolCallMessage`, `finishToolCallMessage`, compact tool bubble markup, flattened tabs, and tab-strip plus button.

8. Add tool event infrastructure:
   - `ToolCallEvent`
   - `ToolCallEventListener`
   - `ToolCallEventPublisher`
   - wrapping provider in `McpToolBridge`
   - Status: started with `ToolCallEvent`, `ToolCallEventListener`, and observable `McpToolBridge` callbacks.

9. Render tool bubbles:
   - CSS for running/success/error tool states.
   - show tool name, concise status, collapsible arguments/result.
   - Status: mostly done. Live and restored persisted tool messages now render through the same tool bubble widget.

10. Verification:
   - create multiple tabs, pick different models, send in parallel.
   - restart Eclipse and confirm tabs/title/model/history restore.
   - run a prompt that invokes MCP tools and confirm tool bubbles appear.
   - close tabs and verify persistence updates.

## Risks And Notes

- Image attachments currently contain SWT `ImageData` and previews; only text/file metadata should be persisted initially.
- Persisting in-flight partial assistant responses across hard shutdown is optional. The first pass should persist only completed assistant messages and completed tool records.
- The global chat model preference should not be removed yet because completion and older flows still use it.
- Tool callbacks may require typed Spring AI API imports. If OSGi visibility blocks them, use a narrow adapter that keeps the rest of the agent path provider-agnostic.
