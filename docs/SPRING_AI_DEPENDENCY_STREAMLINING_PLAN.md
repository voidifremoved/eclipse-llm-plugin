# Spring AI Dependency Streamlining Plan

## Goal

Make the Eclipse plugin build and dependency model boring, repeatable, and easier to evolve while keeping Spring AI as the preferred long-term integration layer.

## Progress summary

| Phase | Status | Notes |
|-------|--------|-------|
| 1 Build reproducibility | **Mostly complete** | Scripts, `.mvn/`, `BUILDING.md`, CI `tycho.localArtifacts=ignore`. Maven wrapper pending (needs cert fix to generate). |
| 2 Separate source from output | **Complete** | `site/` jars gitignored/removed; CI deploys from `releng/.../target/repository`. |
| 3 Centralise versions | **Mostly complete** | Parent POM properties + `docs/DEPENDENCIES.md`. Kept `includeDependencyDepth=direct` with explicit 2nd-level roots; `list-wrapped-bundles.ps1` added. |
| 4 Isolate Spring AI | **Mostly complete** | Package `com.rubberjam.eclipse.assistai.springai` in main plugin (factory, registry, providers, `MessageAdapter`). Separate OSGi bundle deferred until shared API bundle exists. `McpToolBridge` remains in `agent`. |
| 5 Provider migration | **Mostly complete** | Agent chat and code completion on Spring AI. Legacy `*StreamJavaHttpClient` stack and `ChatViewPresenter` removed. |
| 6 Own OSGi metadata | **Started** | OkHttp owned wrapper; `scripts/inventory-p2-repository.ps1` + `docs/OSGI_BUNDLES.md`. |
| 7 Repository verification | **Mostly complete** | Feature-only `category.xml` + `includeAllDependencies`. Full `mvn clean verify` passes. Install steps documented in `BUILDING.md`. |

---

## Step-by-step plan (with completion markers)

### Phase 1: Build Reproducibility

- [x] Add `.mvn/maven.config` with stable local defaults
- [x] Add Windows-friendly scripts (`scripts/build.ps1`, `resolve-target.ps1`, `clean-tycho-cache.ps1`)
- [x] Document Java 21 and certificate/truststore troubleshooting (`docs/BUILDING.md`)
- [x] CI uses `-Dtycho.localArtifacts=ignore`
- [ ] Add Maven wrapper files (`mvnw` / `mvnw.cmd`) — blocked until Maven Central trust is fixed locally
- [ ] Update CI to use the wrapper once committed

### Phase 2: Separate Source from Build Output

- [x] Stop tracking generated p2 artifacts under `site/`
- [x] Add ignore rules for generated update-site jars and metadata
- [x] GitHub Pages deployment copies `releng/.../target/repository` plus authored static files
- [x] Keep authored website assets only under `site/`

### Phase 3: Centralise Versions and Dependency Policy

- [x] Move key dependency versions to parent POM properties
- [x] Document dependency addition rules (`docs/DEPENDENCIES.md`)
- [x] Review switching target `includeDependencyDepth` from `direct` to `infinite` — **keep `direct`**; target already lists required 2nd-level roots (Jackson 3, micrometer, etc.); `infinite` would enlarge the graph without removing explicit entries yet
- [x] Add script to list wrapped bundle symbolic names from a Tycho resolve log (`scripts/list-wrapped-bundles.ps1`)

### Phase 4: Isolate Spring AI

- [x] Create `com.rubberjam.eclipse.assistai.springai` package (in main plugin; separate bundle deferred)
- [x] Move `ChatModelFactory`, `ChatModelRegistry`, providers, `MessageAdapter` into springai
- [x] Export `com.rubberjam.eclipse.assistai.springai` packages from main manifest; provider SDK `Import-Package` on main (not on agent UI classes)
- [ ] Move `McpToolBridge` when MCP registry is exposed without a circular bundle dependency
- [ ] Reduce remaining Spring AI imports in main (`AgentSession`, `McpToolBridge`) behind a higher-level SPI

### Phase 5: Transition Providers to Spring AI

- [x] Agent chat UI uses Spring AI for all providers via `ChatModelFactory` / `OpenAiCompatibleChatModelProvider` / Anthropic / Google GenAI
- [x] Code completion uses Spring AI only (`SpringAiStreamingCompletionClient`)
- [x] `McpToolBridge` filters tools by `ConversationContext.allowedTools` (completion tool subset)
- [x] Removed legacy HTTP clients (`OpenAIStreamJavaHttpClient`, Anthropic, Gemini, Grok, DeepSeek, OpenAI Responses)
- [x] Removed deprecated `ChatViewPresenter`, `SendConversationJob`, `ExecuteFunctionCallJob`, and related subscribers
- [ ] MCP tool bridge integration tests for Spring AI completion path

### Phase 6: Own OSGi Metadata

- [x] Inventory script for repository plugins (`scripts/inventory-p2-repository.ps1`)
- [x] Document embed vs wrapper policy (`docs/OSGI_BUNDLES.md`)
- [ ] Decide embed vs wrapper vs target-generated for Tomcat, MCP JSON, Spring stack (per release review)
- [ ] Tycho target filters for duplicate package providers

### Phase 7: Repository and Install Verification

- [x] Simplify `category.xml` to feature-only
- [x] Configure `includeAllDependencies` on repository module
- [x] Document clean-install smoke test from generated p2 site (`docs/BUILDING.md`)
- [ ] Verify update from public update site if compatibility matters

---

## Target architecture

- **Main plugin** — Eclipse UI, MCP, preferences, agent UI (`AgentSession`, `McpToolBridge`), Spring AI completion, and the **`com.rubberjam.eclipse.assistai.springai`** package (factory, registry, providers, message adapter)
- **Future Spring AI OSGi bundle** — optional once a small `assistai.core` API bundle breaks the reactor cycle
- **OkHttp wrapper** — `wrapped.com.squareup.okhttp3.okhttp`

See `docs/DEPENDENCIES.md` for how to add dependencies.

## Immediate next work

1. Generate Maven wrapper when local PKIX/trust is fixed; switch CI to `mvnw`.
2. Add integration tests for Spring AI completion with MCP tool filtering.
3. Phase 6: run `inventory-p2-repository.ps1` on release builds; decide owned wrappers for Tomcat / MCP JSON.
4. Phase 4: thin `AgentSession` behind SPI; optional `assistai.core` bundle for separate Spring AI plugin.
