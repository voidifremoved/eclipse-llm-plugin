# Dependency Management

This document describes how to add or change dependencies for AssistAI.

Version numbers are centralized in the root `pom.xml` `<properties>` section. When bumping a library, update the property there and the matching entries in `releng/com.rubberjam.eclipse.assistai.target/com.rubberjam.eclipse.assistai.target`.

## Decision tree

| Kind of dependency | Where to add it |
|--------------------|-----------------|
| Eclipse platform bundle (UI, JDT, EGit, …) | Target file p2 location (`org.eclipse.sdk`, explicit IUs, or planner) |
| Maven library already published as OSGi bundle | Target file Maven location (no `missingManifest`) |
| Maven library without OSGi metadata | Target file Maven location with `missingManifest="generate"`, or a dedicated wrapper plugin under `plugins/` |
| Runtime-only embed (discouraged) | Plugin `lib/` + `Bundle-Classpath` + `build.properties` — document why embed is required |
| Spring AI / provider SDK code | `plugins/com.rubberjam.eclipse.assistai.springai` — keep out of the main UI plugin |

## Target platform Maven location

The target uses:

```xml
<location includeDependencyDepth="direct" … missingManifest="generate" type="Maven">
```

`direct` means only declared roots are listed; Tycho does **not** automatically add second-level Maven dependencies. If resolution fails with “package X not found”, either:

1. Add the missing artifact as another root `<dependency>` in the target file, or
2. Re-evaluate switching to `includeDependencyDepth="infinite"` on a branch only after trimming explicit 2nd-level roots (current policy: keep `direct` + documented explicit transitives).

To list wrapped bundle symbolic names from a Tycho build log:

```powershell
.\scripts\list-wrapped-bundles.ps1 build.log
```

During `mvn verify`, Tycho logs lines such as:

```text
org.example:artifact:1.0 is wrapped as a bundle with bundle symbolic name wrapped.org.example.artifact
```

Use that symbolic name in manifests and feature metadata — **not** guessed names like `org.example.artifact`.

## Update site / p2 repository

`releng/com.rubberjam.eclipse.assistai.repository/category.xml` lists only the feature.

`tycho-p2-repository-plugin` is configured with:

- `includeAllDependencies=true` — publish transitive bundles required by the feature
- `filterProvided=true` — omit Eclipse platform bundles already on the release train
- `addPomRepositoryReferences=true` — reference Eclipse p2 for the rest

Do **not** hand-maintain long bundle lists in `category.xml`.

## Module layout

| Module | Role |
|--------|------|
| `com.rubberjam.eclipse.plugin.assistai.main` | Eclipse UI, MCP tools, legacy HTTP clients, agent session UI |
| `com.rubberjam.eclipse.assistai.springai` (package in main plugin) | Spring AI factory, registry, providers, message adapter |
| `com.rubberjam.eclipse.assistai.osgi.okhttp` | OkHttp / Okio / Kotlin OSGi wrapper |

The main plugin must not grow new direct imports of Spring AI provider SDKs — add them to the Spring AI bundle instead.
