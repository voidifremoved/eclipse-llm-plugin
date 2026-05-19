# OSGi bundle inventory (Phase 6)

The update site bundles third-party JARs as Tycho-wrapped OSGi plugins (`wrapped.*` symbolic names). Use this doc when deciding which dependencies need owned manifests vs target-generated wrappers.

## Generate inventory

After `mvn verify`:

```powershell
.\scripts\inventory-p2-repository.ps1
```

Archive output when cutting a release:

```powershell
.\scripts\inventory-p2-repository.ps1 -OutFile docs\inventory\assistai-1.0.7-bundles.txt
```

To list wrapped Maven names from a Tycho resolve log:

```powershell
.\mvnw.cmd -DskipTests package 2>&1 | Tee-Object build.log
.\scripts\list-wrapped-bundles.ps1 build.log
```

## Embed vs wrapper vs target-generated (decisions)

| Artifact | Approach | Rationale |
|----------|----------|-----------|
| **OkHttp / Okio** | **Owned wrapper** | `plugins/com.rubberjam.eclipse.assistai.osgi.okhttp` — explicit MANIFEST; Tycho exclusions on SDK okhttp clients |
| **Kotlin** | **Target `kotlin-osgi-bundle`** | Single provider `org.jetbrains.kotlin.osgi-bundle` 1.8.21; **not** embedded in OkHttp wrapper; exclude `kotlin-stdlib*` from OpenAI/Anthropic/GenAI roots |
| **Tomcat embed core** | **Embedded in main** | `lib/tomcat-embed-core-*.jar` on main `Bundle-Classpath`; servlet API from p2 `jakarta.servlet-api`, not from Tomcat’s transitive servlet JAR |
| **MCP JSON Jackson2** | **Embedded in main** | `lib/mcp-json-jackson2-2.0.0-M2.jar` for ServiceLoader wiring; **not** a target root; excluded from `mcp` / `mcp-core` / `spring-ai-mcp` |
| **MCP `mcp` + `mcp-core`** | **Target-generated** | Explicit roots at 2.0.0-M2; `spring-ai-mcp` excludes older MCP 1.x transitives |
| **Spring AI + Spring Framework** | **Target-generated** | `missingManifest=generate`; explicit Spring 7 roots; `slf4j-api` excluded (use Eclipse `org.slf4j`) |
| **Jackson 2 + Jackson 3** | **Target-generated** | Coexist: `com.fasterxml.jackson.*` (2.x) and `tools.jackson.*` (3.x) — different package namespaces |
| **NetworkNT json-schema-validator** | **Target-generated (3.x only)** | Root `3.0.1` only; 2.x root removed to avoid duplicate `com.networknt.schema` providers |
| **Provider SDKs** (OpenAI, Anthropic, Google GenAI) | **Target-generated** | Explicit roots; okhttp exclusions on `*-client-okhttp` artifacts |

Owned wrappers are reserved for cases where Tycho-generated manifests are wrong or unstable (OkHttp/Kotlin). Embedding is reserved for classpath-local integration (Tomcat, MCP JSON). Everything else stays target-generated with `includeDependencyDepth=direct` and documented exclusions.

## Tycho target exclusions (duplicate package providers)

Maven `<exclusions>` on target roots in `releng/.../assistai.target` (sequence 29+):

| Excluded from | Artifact | Use instead |
|---------------|----------|-------------|
| `mcp`, `mcp-core` | `mcp-json-jackson2` | Embedded JAR on main bundle |
| `spring-ai-mcp` | `mcp`, `mcp-core`, `mcp-json-jackson2` | Explicit MCP 2.0 roots + embed |
| Spring Framework 7 roots | `slf4j-api` | Eclipse platform `org.slf4j` |
| `spring-web` | `jakarta.servlet-api` | p2 IU `jakarta.servlet-api` |
| `openai-java-client-okhttp`, `anthropic-java-client-okhttp` | `okhttp`, `okio-jvm` | `wrapped.com.squareup.okhttp3.okhttp` |
| `openai-java-*`, `spring-ai-openai`, `google-genai`, `anthropic-java-*`, `spring-ai-anthropic`, `spring-ai-google-genai` | `kotlin-stdlib`, `kotlin-stdlib-jdk8` | `org.jetbrains.kotlin.osgi-bundle` (feature + target root) |

Removed duplicate target roots: `json-schema-validator` 2.0.1, `mcp-json-jackson2` (embed only).

If a new resolver conflict appears, prefer a targeted exclusion on the pulling root before adding another explicit target dependency.

Tycho also filters `wrapped.org.jetbrains.kotlin.kotlin-stdlib-jdk8` from the resolved target platform (`pom.xml` `target-platform-configuration`) so only `org.jetbrains.kotlin.osgi-bundle` supplies `kotlin.*` at runtime.

## Release review checklist

1. Run `.\mvnw.cmd clean verify` (or `-DskipTests` for a faster compile check).
2. Run `.\scripts\inventory-p2-repository.ps1 -OutFile docs\inventory\assistai-<version>-bundles.txt`.
3. Compare wrapped Spring/MCP bundle counts to prior inventory; investigate new `wrapped.*` names.
4. Confirm feature `feature.xml` still lists only first-party plugins (OkHttp wrapper); transitives come from `includeAllDependencies`.
