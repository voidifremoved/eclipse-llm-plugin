# Spring AI Dependency Streamlining Plan

## Goal

Make the Eclipse plugin build and dependency model boring, repeatable, and easier to evolve while keeping Spring AI as the preferred long-term integration layer.

The target architecture is:

- Eclipse UI, MCP server tools, workspace integration, and preferences stay in the main plugin.
- Provider-specific chat/agent behavior moves toward Spring AI abstractions.
- Legacy handwritten HTTP clients are retired gradually once matching Spring AI behavior exists.
- Third-party dependencies are resolved through a small number of explicit build mechanisms, not a mixture of target-file transitive chasing, checked-in jars, generated bundle metadata, and manual repository category entries.

## Current Assessment

The repository already has several Eclipse plugin best-practice building blocks:

- A Tycho multi-module reactor.
- A PDE target definition shared by Eclipse and Tycho.
- Separate plugin, feature, test plugin, target, and p2 repository modules.
- CI using Java 21 and `mvn clean verify`.

The brittle behavior is coming from the dependency layer rather than the overall project shape:

- The target definition manually lists a large Maven graph, including many transitive dependencies.
- `includeDependencyDepth="direct"` means humans have to discover and add second-level dependencies by hand.
- `missingManifest="generate"` creates generated OSGi metadata for many non-OSGi artifacts.
- The main plugin imports a wide mix of Spring AI, Spring Framework, Reactor, Micrometer, MCP SDK, servlet, provider SDK, and Eclipse packages directly.
- Some jars are checked into plugin `lib/` folders while related dependencies are also represented in the target platform.
- The repository category manually lists many generated or wrapped dependency bundles.
- Generated update-site binaries live under `site/`, making source and build output harder to distinguish.
- Local command-line reproducibility is weak. A local build using `-Dtycho.localArtifacts=ignore` currently fails before compilation because the JDK/Maven truststore cannot validate the Eclipse release repository certificate.

## Guiding Principles

1. Keep Spring AI, but isolate it.

   Spring AI should be the provider abstraction for chat/agent behavior, but the entire Eclipse plugin should not directly depend on the whole Spring AI dependency graph. Put Spring AI behind a small internal service boundary.

2. Use one dependency source of truth.

   Prefer the target platform plus Tycho/Maven dependency resolution. Avoid checked-in jars unless a dependency is deliberately embedded and documented.

3. Prefer explicit wrapper bundles over accidental generated metadata for hard dependencies.

   Generated OSGi manifests are acceptable as a temporary bridge, but the final state should own metadata for libraries whose OSGi behavior matters at runtime.

4. Move incrementally.

   The existing handwritten clients are working behavior. Replace them provider by provider with Spring AI-backed implementations, with tests and fallback behavior during the transition.

5. Make failure cheap.

   Build, target resolution, clean install, and dependency diagnostics should be one-command operations on Windows and CI.

## Target Architecture

### Main Plugin

`com.rubberjam.eclipse.plugin.assistai.main`

Responsibilities:

- Eclipse UI and command handlers.
- Preferences and model configuration storage.
- MCP server registration and Eclipse service tools.
- Agent/session orchestration at the application level.
- Dependency on a small internal chat/agent interface.

The main plugin should avoid direct imports from provider SDKs where practical.

### Spring AI Adapter Bundle

Proposed bundle:

`com.rubberjam.eclipse.assistai.springai`

Responsibilities:

- Own Spring AI imports.
- Own provider-specific Spring AI model construction.
- Adapt existing `ModelApiDescriptor`/preference data into Spring AI options.
- Expose a small service to the main plugin, for example `AssistAiChatModelFactory`.
- Hide OpenAI, Anthropic, Google GenAI, Reactor, Micrometer, and Spring Framework details from UI code.

This bundle can start as an internal package split if a new bundle is too disruptive, but the preferred end state is a separate plugin so OSGi dependencies are isolated.

### Legacy Client Compatibility Layer

The current handwritten clients should remain temporarily:

- `OpenAIStreamJavaHttpClient`
- `OpenAIResponsesJavaHttpClient`
- `AnthropicStreamJavaHttpClient`
- `GeminiStreamJavaHttpClient`
- `GrokStreamJavaHttpClient`
- `DeepSeekStreamJavaHttpClient`

Migration rule:

- New agent features use Spring AI first.
- Existing chat behavior can stay on legacy clients until the equivalent Spring AI provider has tests.
- Remove each legacy client only after its provider path is fully covered.

## Dependency Management End State

The desired final build model is:

- Maven wrapper committed.
- Java 21 explicitly documented and enforced.
- Parent POM centralises dependency versions.
- Target definition contains Eclipse platform and p2 dependencies.
- Maven dependencies are provided through Tycho in a consistent strategy, preferably parent-level dependency management plus `pomDependencies`.
- Checked-in runtime jars are removed unless explicitly documented as embedded dependencies.
- Repository category primarily publishes the feature; dependencies are included through p2 resolution rather than a hand-maintained list.
- CI runs with `-Dtycho.localArtifacts=ignore` to catch accidental reliance on locally installed bundles.

## Step-by-Step Plan

### Phase 1: Build Reproducibility

1. Add Maven wrapper files.
2. Add `.mvn/maven.config` with stable local defaults.
3. Add Windows-friendly scripts:
   - `scripts/build.ps1`
   - `scripts/resolve-target.ps1`
   - `scripts/clean-tycho-cache.ps1`
4. Document Java 21 and certificate/truststore troubleshooting.
5. Update CI to use the wrapper.
6. Add a CI or local check that uses `-Dtycho.localArtifacts=ignore`.

Acceptance criteria:

- A fresh checkout has a documented one-command build.
- The build does not depend on local Tycho artifacts.
- Windows PowerShell commands avoid dotted-property parsing issues.

### Phase 2: Separate Source from Build Output

1. Stop tracking generated p2 artifacts under `site/`.
2. Keep authored website/update-site static assets only.
3. Generate installable content from `releng/com.rubberjam.eclipse.assistai.repository/target/repository`.
4. Add ignore rules for generated update-site jars and metadata.
5. Ensure GitHub Pages deployment copies generated repository output plus authored static files.

Acceptance criteria:

- No generated plugin, feature, `content.jar`, or `artifacts.jar` files are required in source control.
- A clean build can regenerate the installable update site.

### Phase 3: Centralise Versions and Dependency Policy

1. Move repeated dependency versions to parent POM properties.
2. Document dependency addition rules:
   - Eclipse platform bundle: add to target p2 units or manifest.
   - Maven library with usable OSGi metadata: add through Maven/Tycho.
   - Maven library without OSGi metadata: prefer dedicated wrapper bundle if runtime-sensitive.
   - Temporary generated manifest: allowed only with a tracking note.
3. Review whether `includeDependencyDepth="direct"` can be removed.
4. If direct depth remains, generate a dependency inventory script so transitive additions are not manual guesswork.

Acceptance criteria:

- Dependency versions are easy to find.
- New dependencies have a clear path.
- The target file gets shorter or becomes generated from a documented process.

### Phase 4: Isolate Spring AI

1. Create a Spring AI adapter boundary.
2. Move Spring AI provider construction out of the main UI/plugin classes.
3. Keep the main plugin talking to a small internal interface.
4. Move provider SDK imports behind the Spring AI adapter.
5. Move Spring Framework, Reactor, Micrometer, OpenAI SDK, Anthropic SDK, and Google GenAI imports out of UI-facing code where practical.

Acceptance criteria:

- The main plugin has fewer direct third-party imports.
- Spring AI dependency breakages are localised to the adapter.
- Existing UI and MCP behavior still works.

### Phase 5: Transition Providers to Spring AI

Provider migration order:

1. OpenAI-compatible providers: OpenAI, Grok, Groq, DeepSeek.
2. Anthropic.
3. Gemini / Google GenAI.
4. MCP tool bridge integration.

For each provider:

1. Capture current behavior in focused tests or smoke checks.
2. Implement Spring AI-backed provider.
3. Route a preference-controlled or feature-flagged path through Spring AI.
4. Compare request options, streaming behavior, tool calls, cancellation, and error handling.
5. Remove the handwritten client after parity is proven.

Acceptance criteria:

- Each provider is migrated independently.
- Regressions can be traced to one provider path.
- Legacy clients disappear gradually, not all at once.

### Phase 6: Own OSGi Metadata

1. Inventory generated bundle IDs from the target/repository output.
2. Identify runtime-sensitive libraries:
   - Spring AI
   - Spring Framework
   - Reactor
   - Micrometer
   - provider SDKs
   - MCP SDK
   - OkHttp / Okio / Kotlin
   - Tomcat / servlet API
3. Decide for each dependency family:
   - p2-provided bundle
   - Maven artifact with valid OSGi metadata
   - dedicated wrapper bundle
   - deliberately embedded jar
4. Remove duplicate or conflicting providers using Tycho target filters.

Acceptance criteria:

- No accidental duplicate package providers.
- No unclear mix of embedded jar plus target-platform bundle for the same dependency.
- Runtime OSGi failures can be diagnosed from owned metadata.

### Phase 7: Repository and Install Verification

1. Simplify `category.xml` so the feature is the main published artifact.
2. Add a clean install smoke test against the generated p2 repository.
3. Verify install into a clean Eclipse package.
4. Verify update from the existing public update site if compatibility matters.

Acceptance criteria:

- The generated repository installs cleanly.
- Third-party dependencies are available to p2 without manually maintaining a large category list.
- Release artifacts come from CI, not checked-in binaries.

## Immediate Work Queue

1. Add this plan.
2. Add Maven wrapper and build scripts.
3. Add ignore rules for generated update-site artifacts.
4. Update CI to call the wrapper and include a local-artifacts-disabled verification path.
5. Add a short `docs/BUILDING.md`.
6. Start Spring AI isolation by introducing the internal adapter interface.

