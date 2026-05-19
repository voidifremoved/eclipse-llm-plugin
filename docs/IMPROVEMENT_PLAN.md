# AssistAI — Functional & UI Improvement Plan

Step-by-step roadmap based on the current plugin codebase (May 2026). Use this alongside `docs/AGENT_WORKFLOW_IMPLEMENTATION_PLAN.md` (Spring AI migration, largely done) and `agent_plan.md` (tabbed agent UI, largely done).

---

## 1. Current state assessment

### What works well today

| Area | Status |
|------|--------|
| **MCP as Eclipse API bridge** | Strong: ~10 built-in servers, HTTP exposure for external agents, in-process Spring AI bridge + `BuiltinMcpToolRouter` |
| **Multi-tab agent chat** | Persisted tabs, per-tab model, streaming, stop, attachments |
| **Tool visibility** | Tool-call bubbles with status (started / finished / failed) |
| **Code actions on replies** | Copy, insert, diff, apply patch, new file |
| **Code completion** | Restricted tool allowlist + dedicated prompt (read/navigate only) |
| **Provider coverage** | Spring AI–backed models with `functionCalling` flag |

### Gaps vs a modern “agentic IDE” (Cursor, Claude Code, Copilot Agent)

| Expectation | Current behavior | Severity |
|-------------|------------------|----------|
| **Prefer local workspace tools** | Prompt text only (“prefer eclipse-ide…”). Agent registers **all** enabled MCP tools (~95+). Web search competes with workspace tools. | High |
| **Planning / subtasks** | No plan mode, no task list, no step timeline. `system-prompt.md` is generic; completion prompt forbids “Plan:” output. | High |
| **Workspace context in every turn** | Open file/project injected only for **handler** prompts (`ChatMessageFactory`) and **resource cache**, not for free-form agent chat. | High |
| **Agentic loop transparency** | Spring AI runs tools internally; tool rows are **not** sent back in `conversationHistory` to the model. | Medium |
| **Slash commands in agent** | `/discuss` etc. call `getPrompt("discuss")` but `getPrompt` expects enum name `DISCUSS`; `findPromptByCommandName` exists but is unused. No `${currentFileName}` substitution on agent path. | High (bug) |
| **Skills / project rules** | `.claude/skills/*` and `CLAUDE.md` target **external** MCP clients; not loaded into in-IDE agent system prompt. | Medium |
| **Modes (Ask / Agent / Plan)** | Single agent mode when model supports function calling. | Medium |
| **Tool approval** | All tools auto-run. | Medium (user preference) |
| **MCP context freshness** | System prompt built at tab creation; MCP enable/disable may not refresh prompt text on open tabs. | Medium |
| **Documentation drift** | Skills/README reference `getQuickFixes`; actual API is `getCompilationErrors` + `executeQuickFix`. | Low |

### Direct answers to your examples

**Does it correctly favour tools for the local project?**  
**Partially.** Guidance exists in `AgentSystemPromptBuilder` and `CLAUDE.md`, but there is **no enforcement**: no agent-side allowlist (unlike completion), no tool-tier routing, and no automatic “start by reading open editor / project layout” context. Models can still call `webSearch` or guess from chat history.

**Does it behave sufficiently agentic (subtasks, planning)?**  
**No.** It is a **single-turn-oriented chat** with an **opaque multi-tool loop** inside Spring AI. There is no explicit plan artifact, no checklist UI, no “research → plan → execute → verify” workflow in prompts or UI, and no user-visible iteration budget.

---

## 2. Principles for improvements

1. **Workspace-first** — Default agent behavior should read the Eclipse workspace before editing or answering architecture questions.
2. **Prompt + policy** — Combine better system prompts with **optional** tool allowlists (not prompt-only).
3. **Visible agency** — Show plan steps, tool rounds, and verification (e.g. compilation) in the UI.
4. **Reuse existing MCP** — Prefer extending prompts, `ConversationContext`, and `AgentSystemPromptBuilder` over new protocols.
5. **Incremental delivery** — Fix bugs and context injection before large UI features.

---

## 3. Step-by-step implementation plan

### Phase 0 — Stabilize foundations (1–2 weeks) ✅ Completed

**Goal:** Reliable tools and correct agent inputs.

| Step | Action | Files / notes |
|------|--------|----------------|
| 0.1 | **Fix slash commands in agent** — Use `findPromptByCommandName(command)` then `getPrompt(prompt.name())`; run `ChatMessageFactory.updatePromptText()` (or equivalent) so `${currentFileName}`, `${currentProjectName}`, `${message}` work. | `AgentViewPresenter.java`, `ChatMessageFactory.java` |
| 0.2 | **Autocomplete slash commands** — Wire `PromptRepository.findMatchingCommands` / `listCommands` to agent input (completion already has patterns). | `ChatView.java`, `AgentViewPresenter.java` |
| 0.3 | **Include tool results in model history** — When rebuilding `conversationHistory`, include tool messages (or Spring AI tool-response messages) so multi-step reasoning is consistent. | `AgentChatSession.java` |
| 0.4 | **Refresh system prompt on MCP changes** — On `refreshMcpToolsOnAllSessions()`, rebuild system message (MCP list + workspace blurb) per tab, not only tool callbacks. | `AgentSessionManager.java`, `AgentSystemPromptBuilder.java` |
| 0.5 | **Align docs with tools** — Replace `getQuickFixes` with `getCompilationErrors` / `executeQuickFix` in README and `.claude/skills`. | `README.md`, `.claude/skills/eclipse-analyze/SKILL.md` |

**Done when:** `/discuss foo` expands with editor context; agent logs show tool results affecting follow-up turns; MCP pref changes update open tabs.

---

### Phase 1 — Workspace-first agent policy (2–3 weeks) ✅ Completed

**Goal:** Structurally favour local project tools over web/utility tools.

| Step | Action | Files / notes |
|------|--------|----------------|
| 1.1 | **Define agent tool tiers** — e.g. `WORKSPACE` (eclipse-ide, eclipse-coder, eclipse-runner, eclipse-context, eclipse-git, eclipse-pde), `UTILITY` (memory, time), `WEB` (duck-duck-search, webpage-reader). | New `AgentToolPolicy.java` or prefs model |
| 1.2 | **Apply tier to agent via `ConversationContext`** — Default: all workspace tier + memory; optional prefs to enable WEB. Pass context into `McpToolBridge.getToolCallbacks(context, listener)`. | `McpToolBridge.java`, `AgentChatSession.java` |
| 1.3 | **Expand system prompt** — Inject: active project name, open editor file, selection summary (from `PromptContextValueProvider`), enabled tool **categories**, and a short **tool cookbook** (10–15 high-value tools with one-line when-to-use). | `AgentSystemPromptBuilder.java`, new `prompts/agent-workspace.md` fragment |
| 1.4 | **“Workspace agent” preset in MCP preferences** — One-click: enable workspace servers, disable web servers, set default tier. | `McpServerPreferencePage.java` |
| 1.5 | **Inject eclipse skills into agent** — Load condensed text from `.claude/skills/eclipse-*.md` (or bundle `prompts/agent-skills-summary.md`) into system prompt when “Use Eclipse skills” is checked. | `AgentSystemPromptBuilder.java`, preference flag |

**Done when:** With default preset, a “fix errors in this file” request uses `getCompilationErrors` / `applyPatch` without calling web search; system prompt lists current project/file.

---

### Phase 2 — Agentic workflow (plan → execute → verify) (3–4 weeks)

**Goal:** Break work into visible subtasks and enforce verify-after-edit.

| Step | Action | Files / notes |
|------|--------|----------------|
| 2.1 | **Add agent-specific system prompt** — Replace generic `system-prompt.md` for agent with `agent-system-prompt.md`: investigate workspace first; outline plan for non-trivial tasks; prefer small verified steps; call `getCompilationErrors` after edits. | `prompts/`, `PreferenceInitializer.java` |
| 2.2 | **Plan mode (lightweight)** — Optional first pass: model outputs structured plan (markdown checklist) **without tools**; user approves; second pass executes with tools. Toggle in toolbar. | `AgentViewPresenter.java`, `AgentChatSession.java` (two-phase send) |
| 2.3 | **Subtask UI** — Parse checklist from assistant message (or dedicated JSON block) and render sticky “Tasks” panel above input; check off as tools complete (heuristic: tool name matches step keywords). | `ChatView.java` (HTML/JS), CSS |
| 2.4 | **Verification hook** — After `eclipse-coder__*` tool success, optionally auto-call `eclipse-ide__getCompilationErrors` for same project (configurable). Surface result in tool bubble footer. | `McpToolBridge.ObservableToolCallback`, preference |
| 2.5 | **Iteration limits** — Expose max tool rounds per user message (default 25); show “Round N” in UI; stop with clear message when exceeded. | Spring AI advisor config or wrapper, `AgentViewPresenter` |

**Done when:** User can enable plan mode and see a checklist; multi-file refactor shows compile verification without being asked.

---

### Phase 3 — UI/UX improvements (2–3 weeks, parallel with Phase 2)

**Goal:** Agent view feels intentional, not “chat with hidden tools”.

| Step | Action | Files / notes |
|------|--------|----------------|
| 3.1 | **Interaction modes** — Toolbar: **Ask** (no tools / allowlist read-only), **Agent** (workspace tools), **Plan** (plan-only pass). Store per tab. | `AgentTabDescriptor`, `ChatView` toolbar |
| 3.2 | **Context panel** — Collapsible sidebar: active project, open file, selection, linked resources from `ResourceCache`, “Add to context” from Project Explorer. | New `AgentContextPanel.java`, `ChatView` layout |
| 3.3 | **Tool call UX** — Group parallel tools; show duration; truncate large JSON; “Open in editor” for paths in results; link tool name to preference doc. | `ChatView` tool bubble templates |
| 3.4 | **Model & capability hints** — If selected model has `functionCalling == false`, show banner: “Tools disabled for this model.” | `AgentViewPresenter.initializeAvailableModels` |
| 3.5 | **@ mentions** — `@File`, `@Project`, `@Selection` insert context into message or resource cache (Cursor-style). | Input handler in `ChatView` |
| 3.6 | **Keyboard & commands** — Ctrl+Enter send; Ctrl+Shift+Stop; command palette entries for “New Agent Tab”, “Clear Tab”, “Focus Agent View”. | `plugin.xml`, handlers |

**Done when:** User can switch Ask/Agent without changing global model prefs; context panel shows what the model should know.

---

### Phase 4 — Smarter routing & performance (2 weeks)

**Goal:** Less noise, faster responses, safer edits.

| Step | Action | Files / notes |
|------|--------|----------------|
| 4.1 | **Tool schema pruning** — For agent, register full schemas only for workspace tier; for WEB tier register 1–2 tools or hide until user @web. Reduces token load. | `McpToolBridge`, Spring AI callback filter |
| 4.2 | **Suggested tool sequences** — In prompt: “for renames use getClassOutline → refactorRenameSymbol → getCompilationErrors”. | `prompts/agent-workspace.md` |
| 4.3 | **Builtin router metrics** — Log when `BuiltinMcpToolRouter` vs MCP transport is used; detect slow tools. | `BuiltinMcpToolRouter`, ILog |
| 4.4 | **Optional tool approval** — Preference: prompt before `eclipse-coder__*` or `eclipse-git__commit`; timeout auto-deny. | New approval dialog, `McpToolBridge` |

**Done when:** Agent sessions with 6 MCP servers still fit reasonable context; destructive git/edit ops can require confirm.

---

### Phase 5 — Parity with external agent story (ongoing)

**Goal:** In-IDE agent ≈ Claude Code + HTTP MCP, without leaving Eclipse.

| Step | Action | Files / notes |
|------|--------|----------------|
| 5.1 | **Unified tool naming in docs** — Table: HTTP (`getSource`), Spring AI (`eclipse-ide__getSource`), Claude Code (`mcp__eclipse-ide__getSource`). | `README.md`, `CLAUDE.md` |
| 5.2 | **“Copy MCP config for Claude Code”** — From HTTP MCP pref page, copy JSON snippet including token placeholder. | `McpHttpServerPreferencePage` |
| 5.3 | **PDE in defaults** — Mention `eclipse-pde` in agent prompt and HTTP quick-start for RCP/plugin shops. | `AgentSystemPromptBuilder`, README |
| 5.4 | **Eval scenarios** — Scripted tasks: “fix compile error in class X”, “add unit test”, “run tests” — run against agent with workspace preset. | `tests/` or `docs/evals/` |

---

## 4. Recommended priority order

If time is limited, implement in this order:

1. **Phase 0** (bugs + history + slash commands) — highest ROI, low risk  
2. **Phase 1** (workspace tool policy + context injection) — answers “local project first”  
3. **Phase 2.1 + 2.4** (agent prompt + post-edit compile check) — minimum viable “agentic”  
4. **Phase 3.1 + 3.2** (Ask/Agent modes + context panel) — user-visible structure  
5. **Phase 2.2–2.3** (plan mode + checklist UI) — full planning UX  
6. **Phase 4–5** — polish and external-agent parity  

---

## 5. Success criteria (acceptance)

| Scenario | Expected behavior after Phases 0–2 |
|----------|-----------------------------------|
| User asks “What does this class do?” with editor open | Agent calls `getClassOutline` / `getMethodSource` on current file before answering |
| User asks “Fix the error in this file” | Agent uses `getCompilationErrors`, applies `executeQuickFix` or patch, re-checks errors |
| User runs `/refactor` with selection | Expanded prompt includes selection + file; tools enabled |
| User enables Plan mode for “migrate package X” | Checklist shown; execution pass uses tools; steps checked heuristically |
| User disables web MCP servers | Agent cannot call `webSearch`; no regression in workspace tools |
| Non–function-calling model selected | Clear UI warning; Ask mode still works |

---

## 6. Out of scope (for this plan)

- Replacing Spring AI with a custom agent runtime (already migrated; see `AGENT_WORKFLOW_IMPLEMENTATION_PLAN.md`)  
- New LLM providers (unless required for tool calling)  
- Cloud/sync of chat history  
- Full duplicate of Cursor Tab completion (keep separate completion pipeline)  

---

## 7. Related documents

| Document | Purpose |
|----------|---------|
| [AGENT_WORKFLOW_IMPLEMENTATION_PLAN.md](./AGENT_WORKFLOW_IMPLEMENTATION_PLAN.md) | Spring AI architecture migration |
| [agent_plan.md](../agent_plan.md) | Tabbed agent + tool bubbles (implementation log) |
| [BUILDING.md](./BUILDING.md) | Build/install |
| [CLAUDE.md](../CLAUDE.md) | MCP conventions for external agents |
| `.claude/skills/eclipse-*` | Workflows for Claude Code (should be mirrored in-IDE in Phase 1.5) |

---

*Last updated: 2026-05-19 — reflects codebase including Spring AI agent path, `McpToolBridge`, `BuiltinMcpToolRouter`, and multi-tab `AgentViewPresenter`.*
