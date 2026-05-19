# Building AssistAI

## Prerequisites

- Java 21.
- Maven 3.9.x until the Maven wrapper is generated and committed.
- Network access to Maven Central and `https://download.eclipse.org/releases/2026-03/`.

## Modules

| Module | Purpose |
|--------|---------|
| `releng/com.rubberjam.eclipse.assistai.target` | Shared target platform |
| `plugins/com.rubberjam.eclipse.plugin.assistai.main` | Main Eclipse plugin |
| `plugins/.../assistai/springai/` (source package) | Spring AI chat models and providers (same main plugin) |
| `plugins/com.rubberjam.eclipse.assistai.osgi.okhttp` | OkHttp OSGi wrapper |
| `releng/com.rubberjam.eclipse.assistai.repository` | p2 update site |

## Standard Build

On Windows PowerShell:

```powershell
.\scripts\build.ps1
```

To skip tests during dependency or target-platform work:

```powershell
.\scripts\build.ps1 -SkipTests
```

The scripts pass `-Dtycho.localArtifacts=ignore` by default so the build does not accidentally depend on bundles installed in your local Maven repository. If you intentionally want to allow local artifacts while experimenting:

```powershell
.\scripts\build.ps1 -AllowLocalArtifacts
```

## Target Resolution Check

To run a smaller check focused on resolving the main plugin target:

```powershell
.\scripts\resolve-target.ps1
```

## Clearing Tycho/Maven Transfer Cache

If Tycho or Maven cached a failed repository lookup:

```powershell
.\scripts\clean-tycho-cache.ps1 -IncludeMavenTransferCache
```

## Known Local Certificate Failure

A local build currently fails on this machine before compilation with a PKIX certificate error while accessing Maven Central or the Eclipse release repository. Fix the Java truststore or corporate proxy certificate before generating the Maven wrapper or expecting target resolution to complete.

Typical symptoms:

```text
PKIX path building failed: unable to find valid certification path to requested target
```

After fixing the truststore, generate the Maven wrapper from the repository root:

```powershell
mvn -N wrapper:wrapper
```

Then use:

```powershell
.\mvnw.cmd clean verify
```

## Local update site output

After a successful build, the installable p2 repository is at:

`releng/com.rubberjam.eclipse.assistai.repository/target/repository`

In Eclipse: *Help → Install New Software → Add → Local…* and select that folder.

## Install smoke test (local p2)

After `mvn clean verify` (or `.\scripts\build.ps1`):

1. Open a fresh Eclipse SDK 2026-03 (or your dev product) with no prior AssistAI install.
2. *Help → Install New Software → Add → Local…* → `releng/com.rubberjam.eclipse.assistai.repository/target/repository`.
3. Select the **AssistAI** category / feature and complete install; restart when prompted.
4. Confirm *Window → Show View → Other → AssistAI* (or your Agent view) opens without errors in *Error Log*.
5. Optional: uninstall via *About → Installation Details* to verify the site is consistent.

To capture wrapped Maven bundle symbolic names from a build log:

```powershell
mvn -DskipTests package 2>&1 | Tee-Object build.log
.\scripts\list-wrapped-bundles.ps1 build.log
```

