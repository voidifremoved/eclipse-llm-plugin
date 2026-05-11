# Agent Workflow Implementation Plan

## Objective

Replace the current rudimentary chat interface with an agentic workflow powered by **Spring AI**, enabling:

- A Cursor-style agent interface with iterative tool-calling loops
- Runtime model selection across all configured providers (OpenAI, Anthropic, Gemini, Grok, DeepSeek, Groq)
- Automatic access to the built-in MCP server tools for every agent session
- Streaming token-by-token output with concurrent tool execution

---

## Current Architecture Summary

### What exists today

| Layer | Implementation | Key Classes |
|-------|---------------|-------------|
| **UI** | E4 `PartDescriptor` with `Browser` (SWT.EDGE) + SWT input | `ChatView`, `ChatViewPresenter` |
| **Orchestration** | Eclipse Jobs + manual `onContinue` callbacks | `SendConversationJob`, `ExecuteFunctionCallJob` |
| **LLM Clients** | 6 hand-rolled HTTP streaming clients | `OpenAIStreamJavaHttpClient`, `AnthropicStreamJavaHttpClient`, `GeminiStreamJavaHttpClient`, `GrokStreamJavaHttpClient`, `DeepSeekStreamJavaHttpClient`, `OpenAIResponsesJavaHttpClient` |
| **Tool execution** | Custom `FunctionCallSubscriber` → MCP `callTool` | `FunctionCallSubscriber`, `ExecuteFunctionCallJob` |
| **MCP tools** | In-memory `McpSyncClient` / `McpSyncServer` pairs via MCP SDK | `InMemoryMcpClientRetistry`, `McpServerFactory`, `InMemoryClientServerFactory` |
| **Model config** | JSON in Eclipse preferences | `ModelApiDescriptor`, `ModelApiDescriptorRepository` |
| **DI** | Eclipse E4 (`@Creatable`, `@Singleton`, `@Inject`, `IEclipseContext`) | `Activator.make()`, `ContextInjectionFactory` |
| **Conversation** | In-memory `LinkedList<ChatMessage>` | `Conversation`, `ChatMessage`, `ConversationContext` |
| **Streaming** | `SubmissionPublisher<Incoming>` + `Flow.Subscriber` | `AppendMessageToViewSubscriber`, `PrintMessageSubscriber` |

### Pain points addressed by this plan

1. **Six separate HTTP client implementations** with duplicated SSE parsing, JSON construction, tool-call marshalling, and error handling
2. **Manual agent loop** — `FunctionCallSubscriber` reassembles tool-call JSON fragments, `ExecuteFunctionCallJob` executes tools, `onContinue` callback re-schedules `SendConversationJob` — fragile and hard to extend
3. **No unified streaming+tools** — tool calling and streaming are handled through different code paths
4. **Tight coupling** between LLM protocol details and chat view presentation
5. **Adding a new provider** requires writing a new client class, updating `AbstractLanguageModelHttpClientProvider`'s switch expression, and threading through the provider chain

---

## Target Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ChatView (UI)                               │
│   Browser (SWT.EDGE) + SWT input + model selector + toolbar        │
│   Renders: markdown → HTML, tool-call status, streaming tokens     │
└────────────────────────┬────────────────────────────────────────────┘
                         │ Events (send, stop, clear, model-change)
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    AgentSessionManager                              │
│   Creates/manages AgentSession per conversation                    │
│   Handles model switching, session lifecycle                       │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      AgentSession                                   │
│   Owns: Spring AI ChatClient, Conversation history,                │
│         MCP ToolCallbacks, system prompt                           │
│   Delegates agent loop to Spring AI (ToolCallAdvisor)              │
└──────┬─────────────────────────────┬────────────────────────────────┘
       │                             │
       ▼                             ▼
┌──────────────┐    ┌───────────────────────────────────────────┐
│  ChatModel   │    │        SyncMcpToolCallbackProvider        │
│  (Spring AI) │    │  Wraps InMemoryMcpClientRetistry clients  │
│              │    │  into Spring AI ToolCallback[]             │
│  Created via │    └───────────────────────────────────────────┘
│  ChatModel   │
│  Factory     │
└──────────────┘
```

### Key design decisions

1. **Spring AI as orchestration layer only** — replace the 6 hand-rolled HTTP clients and the manual agent loop, but keep Eclipse E4 DI as the top-level wiring framework
2. **Spring AI used without Spring Boot** — `ChatModel` instances created programmatically via builders; no Spring `ApplicationContext`
3. **MCP tools bridged via `SyncMcpToolCallbackProvider`** — reuse the existing `InMemoryMcpClientRetistry` and `McpSyncClient` instances
4. **Streaming preserved** — use `StreamingChatModel.stream()` with the new `ToolCallAdvisor` streaming support
5. **Model switching at runtime** — `AgentSessionManager` rebuilds `ChatClient` when the user selects a different model from the dropdown

---

## Implementation Steps

### Phase 1: Dependencies and Build Infrastructure

#### Step 1.1 — Add Spring AI Maven dependencies to the target platform

Add Spring AI JARs to the Tycho Maven target location in the `.target` file. The required artifacts are:

```xml
<!-- Spring AI core -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-model</artifactId>
    <version>${spring-ai.version}</version>   <!-- 2.0.0-M4 or latest stable -->
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-client-chat</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-commons</artifactId>
    <version>${spring-ai.version}</version>
</dependency>

<!-- Provider modules -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-anthropic</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vertex-ai-gemini</artifactId>
    <version>${spring-ai.version}</version>
</dependency>

<!-- MCP integration -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
```

**Notes:**
- Grok, Groq, and DeepSeek all use OpenAI-compatible APIs → handled by `spring-ai-openai` with custom base URLs
- Spring AI 2.0.0-M4 depends on Reactor (already in target), Jackson (already in target), and SLF4J (already in target)
- Must evaluate transitive dependencies for OSGi bundle compatibility — some may need `missingManifest="generate"` in the target

**Files changed:** `releng/com.github.gradusnikov.eclipse.assistai.target/com.github.gradusnikov.eclipse.assistai.target`

#### Step 1.2 — Update MANIFEST.MF imports

Add `Import-Package` entries for Spring AI packages:

```
org.springframework.ai.chat.model,
org.springframework.ai.chat.client,
org.springframework.ai.chat.prompt,
org.springframework.ai.chat.messages,
org.springframework.ai.model,
org.springframework.ai.openai,
org.springframework.ai.openai.api,
org.springframework.ai.anthropic,
org.springframework.ai.vertexai.gemini,
org.springframework.ai.mcp,
org.springframework.ai.tool
```

**Files changed:** `plugins/.../META-INF/MANIFEST.MF`

#### Step 1.3 — Verify OSGi resolution

Run `mvn clean verify -pl plugins/com.github.gradusnikov.eclipse.plugin.assistai.main` to verify all bundles resolve. Address any missing transitive dependencies by adding them to the target platform.

**Risk:** Spring AI JARs are not OSGi bundles natively. The Tycho Maven target `missingManifest="generate"` setting will auto-generate bundle manifests, but some dependencies (e.g., Spring Framework core, Spring Retry) may pull in a large transitive graph. This step may require iteration.

**Mitigation:** If the transitive dependency footprint is too large, consider embedding key Spring AI JARs in `lib/` (like `mcp-json-jackson2` and `tomcat-embed-core` are today) and adding them to `Bundle-Classpath`.

---

### Phase 2: ChatModel Factory

#### Step 2.1 — Create `ChatModelFactory`

A new factory class that creates Spring AI `ChatModel`/`StreamingChatModel` instances from the existing `ModelApiDescriptor` configuration.

**Location:** `src/.../assistai/agent/ChatModelFactory.java`

```java
@Creatable
@Singleton
public class ChatModelFactory
{
    // Creates a ChatModel from a ModelApiDescriptor
    // Routes to the correct Spring AI provider based on apiUrl patterns
    // (same routing logic as current AbstractLanguageModelHttpClientProvider.createClient)
    
    public ChatModel createChatModel(ModelApiDescriptor descriptor) { ... }
    public StreamingChatModel createStreamingChatModel(ModelApiDescriptor descriptor) { ... }
}
```

**Routing logic** (mirrors existing `createClient` switch):

| `apiUrl` pattern | Spring AI provider | Notes |
|---|---|---|
| contains `anthropic` | `AnthropicChatModel` | Direct Anthropic SDK |
| contains `googleapis` | `VertexAiGeminiChatModel` or OpenAI-compat | Evaluate Gemini API compat |
| contains `/v1/responses` | `OpenAiChatModel` (Responses API mode) | OpenAI Responses endpoint |
| contains `api.x.ai` (Grok) | `OpenAiChatModel` with custom base URL | OpenAI-compatible |
| contains `deepseek` | `OpenAiChatModel` with custom base URL | OpenAI-compatible |
| contains `groq.com` | `OpenAiChatModel` with custom base URL | OpenAI-compatible |
| default | `OpenAiChatModel` | Standard OpenAI |

Each `ChatModel` is created programmatically using the builder pattern:

```java
// Example for OpenAI-compatible providers
var api = OpenAiApi.builder()
    .baseUrl(descriptor.apiUrl())
    .apiKey(descriptor.apiKey())
    .build();
    
return OpenAiChatModel.builder()
    .openAiApi(api)
    .defaultOptions(OpenAiChatOptions.builder()
        .model(descriptor.modelName())
        .temperature(descriptor.scaledTemperature().orElse(null))
        .build())
    .build();
```

**Files changed:** New `agent/ChatModelFactory.java`

#### Step 2.2 — Create `ChatModelRegistry`

A registry that caches `ChatModel` instances by `ModelApiDescriptor.uid()` and invalidates when configuration changes.

**Location:** `src/.../assistai/agent/ChatModelRegistry.java`

```java
@Creatable
@Singleton
public class ChatModelRegistry
{
    private final Map<String, ChatModel> models = new HashMap<>();
    
    @Inject
    private ChatModelFactory factory;
    
    @Inject
    private ModelApiDescriptorRepository descriptorRepository;
    
    public ChatModel getModel(String uid) { ... }
    public StreamingChatModel getStreamingModel(String uid) { ... }
    public void invalidate(String uid) { ... }
    public void invalidateAll() { ... }
}
```

**Files changed:** New `agent/ChatModelRegistry.java`

---

### Phase 3: MCP Tool Bridge

#### Step 3.1 — Create `McpToolBridge`

Bridge the existing `InMemoryMcpClientRetistry` (which holds `McpSyncClient` instances for all enabled MCP servers) to Spring AI's `ToolCallback` interface.

**Location:** `src/.../assistai/agent/McpToolBridge.java`

```java
@Creatable
@Singleton
public class McpToolBridge
{
    @Inject
    private InMemoryMcpClientRetistry mcpClientRegistry;
    
    /**
     * Returns Spring AI ToolCallback[] for all enabled MCP tools.
     * Uses SyncMcpToolCallbackProvider from spring-ai-mcp.
     */
    public ToolCallback[] getToolCallbacks()
    {
        List<McpSyncClient> clients = new ArrayList<>(
            mcpClientRegistry.listEnabledClients().values()
        );
        return SyncMcpToolCallbackProvider.syncToolCallbacks(clients)
                                          .toArray(new ToolCallback[0]);
    }
    
    /**
     * Returns tool callbacks filtered for a specific context.
     */
    public ToolCallback[] getToolCallbacks(ConversationContext context) { ... }
}
```

This class is the critical integration point — it makes all the existing MCP servers (eclipse-ide, eclipse-coder, eclipse-runner, etc.) automatically available to Spring AI's agent loop without any changes to the MCP server implementations.

**Files changed:** New `agent/McpToolBridge.java`

---

### Phase 4: Agent Session

#### Step 4.1 — Create `AgentSession`

An `AgentSession` encapsulates a single agent conversation with its `ChatClient`, tools, and history.

**Location:** `src/.../assistai/agent/AgentSession.java`

```java
public class AgentSession
{
    private final String sessionId;
    private ChatClient chatClient;
    private ModelApiDescriptor currentModel;
    private final McpToolBridge toolBridge;
    private final ChatModelFactory modelFactory;
    private final List<Message> conversationHistory;  // Spring AI Message type
    private final String systemPrompt;
    
    // Creates and configures a ChatClient for the current model
    // with MCP tools automatically attached
    public void initialize(ModelApiDescriptor model) { ... }
    
    // Sends a user message and returns a streaming Flux<String>
    // The ChatClient handles the agent loop internally:
    //   1. Send message + history to LLM
    //   2. If LLM returns tool calls → execute via MCP → append results
    //   3. Repeat until LLM returns a text response
    //   4. Stream text tokens to caller
    public Flux<ChatResponse> sendMessage(String userMessage, List<Attachment> attachments) { ... }
    
    // Switch model mid-conversation (preserves history)
    public void switchModel(ModelApiDescriptor newModel) { ... }
    
    // Clear conversation history
    public void clear() { ... }
    
    // Cancel in-progress request
    public void cancel() { ... }
}
```

The key `sendMessage` implementation leverages Spring AI's built-in agent loop:

```java
public Flux<ChatResponse> sendMessage(String userMessage, List<Attachment> attachments)
{
    // Build user message with any media attachments
    UserMessage message = buildUserMessage(userMessage, attachments);
    conversationHistory.add(message);
    
    // Spring AI handles the entire agent loop:
    // - Sends conversation to LLM
    // - If tool_calls in response → executes tools → appends results → re-sends
    // - Continues until LLM produces a text-only response
    // - Streams tokens from the final text response
    return chatClient.prompt()
        .messages(conversationHistory)
        .stream()
        .chatResponse();
}
```

**Files changed:** New `agent/AgentSession.java`

#### Step 4.2 — Create `AgentSessionManager`

Manages the lifecycle of agent sessions, coordinates with the UI.

**Location:** `src/.../assistai/agent/AgentSessionManager.java`

```java
@Creatable
@Singleton
public class AgentSessionManager
{
    @Inject private ChatModelFactory modelFactory;
    @Inject private McpToolBridge toolBridge;
    @Inject private ModelApiDescriptorRepository modelRepository;
    @Inject private PromptRepository promptRepository;
    
    private AgentSession currentSession;
    
    public AgentSession getOrCreateSession() { ... }
    public AgentSession newSession() { ... }
    public void switchModel(String modelUid) { ... }
    public void destroySession() { ... }
}
```

**Files changed:** New `agent/AgentSessionManager.java`

---

### Phase 5: UI Integration — Agent View Presenter

#### Step 5.1 — Create `AgentViewPresenter` (replaces `ChatViewPresenter` role)

The presenter bridges the `AgentSession` to the `ChatView` UI. It subscribes to the `Flux<ChatResponse>` stream and pushes updates to the browser.

**Location:** `src/.../assistai/agent/AgentViewPresenter.java`

```java
@Creatable
@Singleton
public class AgentViewPresenter implements IResourceCacheListener
{
    @Inject private AgentSessionManager sessionManager;
    @Inject private PartAccessor partAccessor;
    @Inject private UISynchronize uiSync;
    @Inject private ModelApiDescriptorRepository modelRepository;
    
    private Disposable currentStream;  // Reactor Disposable for cancellation
    
    public void onSendUserMessage(String text)
    {
        AgentSession session = sessionManager.getOrCreateSession();
        
        // Display user message in UI
        applyToView(view -> {
            view.clearUserInput();
            view.appendMessage(messageId, "user");
            view.setMessageHtml(messageId, text);
            view.setInputEnabled(false);
        });
        
        // Start streaming agent response
        String assistantMessageId = UUID.randomUUID().toString();
        applyToView(view -> view.appendMessage(assistantMessageId, "assistant"));
        
        currentStream = session.sendMessage(text, attachments)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                chatResponse -> {
                    // Stream tokens to UI
                    String content = chatResponse.getResult().getOutput().getText();
                    uiSync.asyncExec(() -> {
                        applyToView(view -> 
                            view.setMessageHtml(assistantMessageId, accumulatedContent));
                    });
                },
                error -> {
                    // Handle errors
                    uiSync.asyncExec(() -> 
                        applyToView(view -> view.setInputEnabled(true)));
                },
                () -> {
                    // Completion
                    uiSync.asyncExec(() -> 
                        applyToView(view -> view.setInputEnabled(true)));
                }
            );
    }
    
    public void onStop()
    {
        if (currentStream != null && !currentStream.isDisposed())
        {
            currentStream.dispose();
        }
    }
    
    public void onModelSelected(String modelId)
    {
        sessionManager.switchModel(modelId);
        modelRepository.setChatModelInUse(modelId);
    }
    
    public void onClear()
    {
        sessionManager.newSession();
        applyToView(ChatView::clearChatView);
    }
}
```

**Files changed:** New `agent/AgentViewPresenter.java`

#### Step 5.2 — Update `ChatView` to use `AgentViewPresenter`

Change `ChatView` to inject `AgentViewPresenter` instead of `ChatViewPresenter`. The view itself does not change significantly — it still renders HTML in a `Browser` widget, handles drag-and-drop, and exposes `BrowserFunction` callbacks. The change is in which presenter it delegates to.

Modify `ChatView`:
- Replace `@Inject ChatViewPresenter presenter` with `@Inject AgentViewPresenter presenter`
- Update event handlers to call the new presenter methods

**Files changed:** `view/ChatView.java`

#### Step 5.3 — Add tool-call status rendering to the UI

Enhance `textview.js` and `textview.css` to show agent tool-call activity inline, similar to Cursor's interface:

- Show a collapsible "Tool Call" indicator when the agent invokes a tool
- Display tool name, arguments summary, and execution status (running/completed/error)
- Show elapsed time for each tool call
- Animate a "thinking" indicator while the agent loop is running

This builds on the existing `updateFunctionCallSummaries()` function in `textview.js`.

**Files changed:** `js/textview.js`, `css/textview.css`

---

### Phase 6: System Prompt and Context Management

#### Step 6.1 — Create `AgentSystemPromptBuilder`

Builds the system prompt for agent sessions. Includes:
- Base system prompt from `prompts/system-prompt.md`
- Available tool descriptions (auto-generated from MCP tool metadata)
- Resource cache contents (files the user has added to context)
- Workspace context (active project, open editor)

**Location:** `src/.../assistai/agent/AgentSystemPromptBuilder.java`

```java
@Creatable
@Singleton
public class AgentSystemPromptBuilder
{
    @Inject private PromptRepository promptRepository;
    @Inject private ResourceCache resourceCache;
    @Inject private McpToolBridge toolBridge;
    
    public String buildSystemPrompt()
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append(promptRepository.getSystemPrompt());
        prompt.append(buildResourceContext());
        return prompt.toString();
    }
    
    private String buildResourceContext() { ... }
}
```

**Files changed:** New `agent/AgentSystemPromptBuilder.java`

---

### Phase 7: Migrate Existing Functionality

#### Step 7.1 — Migrate slash commands

The current `ChatViewPresenter.createUserMessage` resolves `/command` prefixes via `PromptRepository`. Port this to `AgentViewPresenter`:

- When user input starts with `/`, resolve it through `PromptRepository`
- Replace the user message content with the resolved prompt template
- Display the original command name in the UI (as today)

**Files changed:** `agent/AgentViewPresenter.java`

#### Step 7.2 — Migrate predefined prompt handlers

The E4 handlers (`AssistAIDiscussCodeHandler`, `AssistAIRefactorCodeHandler`, etc.) currently call `ChatViewPresenter.onSendPredefinedPrompt`. Update them to call `AgentViewPresenter` instead.

Two approaches:
- **Option A:** Update `AssistAIHandlerTemplate` to inject `AgentViewPresenter` directly
- **Option B:** Use an intermediary interface that both presenters implement, allowing a gradual transition

Recommend **Option A** for simplicity.

**Files changed:** `handlers/AssistAIHandlerTemplate.java`, `view/ChatViewPresenter.java` (deprecate)

#### Step 7.3 — Migrate attachment handling

Port image/file attachment support to `AgentViewPresenter`:
- Image attachments → Spring AI `Media` objects in `UserMessage`
- File content attachments → text content appended to user message

**Files changed:** `agent/AgentViewPresenter.java`

#### Step 7.4 — Migrate code action BrowserFunctions

The existing `BrowserFunction` callbacks (copy code, apply patch, insert code, diff code, new file) operate independently of the LLM client. They can remain on `ChatView` and delegate to utility classes that are already separate from `ChatViewPresenter`:
- `ApplyPatchWizardHelper` — unchanged
- `CodeEditingService` — unchanged
- Clipboard operations — unchanged

**Files changed:** Minimal — verify `ChatView` browser functions still work with new presenter

#### Step 7.5 — Migrate replay/regenerate

The "replay last message" feature (`onReplayLastMessage`) needs to:
1. Remove the last assistant message from the Spring AI conversation history
2. Re-send the conversation
3. Stream the new response

**Files changed:** `agent/AgentSession.java`, `agent/AgentViewPresenter.java`

---

### Phase 8: Conversation History Adapter

#### Step 8.1 — Bridge `ChatMessage` ↔ Spring AI `Message`

Spring AI uses its own `Message` type hierarchy (`UserMessage`, `AssistantMessage`, `SystemMessage`, `ToolResponseMessage`). The existing `ChatMessage` class is used for persistence and UI rendering.

Create a bidirectional adapter:

**Location:** `src/.../assistai/agent/MessageAdapter.java`

```java
public class MessageAdapter
{
    // Convert internal ChatMessage to Spring AI Message
    public static Message toSpringAi(ChatMessage chatMessage) { ... }
    
    // Convert Spring AI Message to internal ChatMessage (for UI display)
    public static ChatMessage fromSpringAi(Message springAiMessage) { ... }
}
```

This allows the `AgentSession` to maintain conversation state in Spring AI `Message` format while the `ChatView` continues to render from `ChatMessage` objects.

**Files changed:** New `agent/MessageAdapter.java`

---

### Phase 9: Remove Legacy Client Infrastructure

#### Step 9.1 — Deprecate hand-rolled HTTP clients

Once the Spring AI integration is stable, mark the following as `@Deprecated`:
- `OpenAIStreamJavaHttpClient`
- `OpenAIResponsesJavaHttpClient`
- `AnthropicStreamJavaHttpClient`
- `GeminiStreamJavaHttpClient`
- `GrokStreamJavaHttpClient`
- `DeepSeekStreamJavaHttpClient`
- `AbstractLanguageModelClient`
- `AbstractLanguageModelHttpClientProvider`
- `ChatLanguageModelHttpClientProvider`

**Do not delete yet** — the code completion feature (`CompletionsLanguageModelHttpClientProvider`) still uses them.

**Files changed:** All client classes in `network/clients/`

#### Step 9.2 — Deprecate manual agent-loop classes

Mark as `@Deprecated`:
- `FunctionCallSubscriber` (Spring AI's `ToolCallAdvisor` replaces this)
- `ExecuteFunctionCallJob` (Spring AI executes tools internally)
- `SendConversationJob` (replaced by `AgentSession.sendMessage`)
- `Incoming` record (Spring AI streams `ChatResponse` objects)
- `AppendMessageToViewSubscriber` (replaced by Reactor subscriber in `AgentViewPresenter`)

**Files changed:** Classes in `network/subscribers/`, `jobs/`, `chat/`

#### Step 9.3 — Migrate code completion to Spring AI (future phase)

The code completion feature (`CompletionsLanguageModelHttpClientProvider`) can be migrated to Spring AI in a separate effort. It uses a restricted tool set and different output handling (ghost text in the editor), so it is best kept as a separate migration.

---

### Phase 10: Testing

#### Step 10.1 — Unit tests for `ChatModelFactory`

Test that each `ModelApiDescriptor` configuration correctly produces the expected Spring AI `ChatModel` subclass with the right base URL, model name, and options.

**Location:** `tests/.../agent/ChatModelFactoryTest.java`

#### Step 10.2 — Unit tests for `McpToolBridge`

Test that enabled MCP clients are correctly converted to `ToolCallback[]` and that tool names are preserved.

**Location:** `tests/.../agent/McpToolBridgeTest.java`

#### Step 10.3 — Unit tests for `MessageAdapter`

Test bidirectional conversion between `ChatMessage` and Spring AI `Message` types, including:
- Text messages
- Messages with attachments/media
- Tool call messages
- Tool result messages

**Location:** `tests/.../agent/MessageAdapterTest.java`

#### Step 10.4 — Integration test for `AgentSession`

Test the full agent loop with a mock `ChatModel` that returns tool calls and then a final text response. Verify that:
- Tools are invoked via `McpToolBridge`
- Conversation history accumulates correctly
- Streaming works end-to-end
- Model switching preserves history

**Location:** `tests/.../agent/AgentSessionIntegrationTest.java`

---

## New Package Structure

All new classes go in a new `agent` package:

```
src/com/github/gradusnikov/eclipse/assistai/
├── agent/
│   ├── ChatModelFactory.java          — Creates Spring AI ChatModel from ModelApiDescriptor
│   ├── ChatModelRegistry.java         — Caches ChatModel instances by UID
│   ├── McpToolBridge.java             — Bridges MCP clients to Spring AI ToolCallback[]
│   ├── AgentSession.java              — Single agent conversation (ChatClient + history + tools)
│   ├── AgentSessionManager.java       — Lifecycle management for agent sessions
│   ├── AgentViewPresenter.java        — Presenter connecting AgentSession to ChatView
│   ├── AgentSystemPromptBuilder.java  — Builds system prompt with context
│   └── MessageAdapter.java           — Bidirectional ChatMessage ↔ Spring AI Message conversion
```

---

## Dependency Impact Assessment

### New dependencies added

| Artifact | Size (approx) | Already in target? | Notes |
|----------|---------------|--------------------|----|
| `spring-ai-model` | ~200KB | No | Core interfaces |
| `spring-ai-client-chat` | ~150KB | No | ChatClient, ChatModel |
| `spring-ai-commons` | ~100KB | No | Shared utilities |
| `spring-ai-openai` | ~300KB | No | OpenAI + OpenAI-compat |
| `spring-ai-anthropic` | ~200KB | No | Anthropic client |
| `spring-ai-vertex-ai-gemini` | ~250KB | No | Gemini client |
| `spring-ai-mcp` | ~100KB | No | MCP ↔ ToolCallback bridge |
| `spring-retry` | ~80KB | No | Transitive from spring-ai |
| `spring-core` (subset) | ~1.5MB | No | Transitive from spring-ai |
| `micrometer-observation` | ~100KB | No | Optional observability |

### Existing dependencies reused

| Artifact | Already in target? |
|----------|-------------------|
| Jackson (2.21.x) | Yes |
| Reactor Core (3.8.x) | Yes |
| SLF4J (2.x) | Yes |
| MCP SDK (1.1.2) | Yes |
| reactive-streams (1.0.4) | Yes |

### Risk: Spring Framework core dependency

Spring AI has a transitive dependency on `spring-core`, `spring-context`, etc. In an OSGi context without Spring Boot, this is dead weight. Two mitigations:

1. **Evaluate the actual required subset** — Spring AI may only need `spring-core` (for `@Nullable`, `Assert`, `StringUtils` etc.) and not the full Spring container
2. **Embed as `Bundle-Classpath` JARs** — like `tomcat-embed-core` today, embed Spring AI + minimal transitive JARs in `lib/`

---

## Migration Strategy

### Parallel operation period

During development, both the old and new systems coexist:
- `ChatViewPresenter` (old) remains functional
- `AgentViewPresenter` (new) is developed alongside
- A preference toggle or separate view allows testing the new agent mode
- Once stable, the old presenter is deprecated and the `ChatView` defaults to the agent presenter

### Backward compatibility

- `ModelApiDescriptor` and `ModelApiDescriptorRepository` are unchanged
- MCP server infrastructure (`McpServerFactory`, `HttpMcpServerRegistry`, `InMemoryMcpClientRetistry`) is unchanged
- `ResourceCache`, `PromptRepository`, and all MCP service classes are unchanged
- The `ChatView` UI is largely unchanged — only the presenter injection changes

---

## Execution Order Summary

| Phase | Description | Depends On |
|-------|-------------|------------|
| **1** | Dependencies and build infrastructure | — |
| **2** | ChatModel factory and registry | Phase 1 |
| **3** | MCP tool bridge | Phase 1 |
| **4** | Agent session (core agent loop) | Phase 2, 3 |
| **5** | UI integration (presenter + view updates) | Phase 4 |
| **6** | System prompt and context management | Phase 4 |
| **7** | Migrate existing functionality | Phase 5, 6 |
| **8** | Conversation history adapter | Phase 4 |
| **9** | Deprecate legacy infrastructure | Phase 7 |
| **10** | Testing | All phases |

Phases 2 and 3 can be done in parallel. Phases 5, 6, and 8 can be done in parallel after Phase 4.
