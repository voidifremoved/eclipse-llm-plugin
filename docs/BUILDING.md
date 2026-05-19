# Building AssistAI

## Prerequisites

- Java 21.
- Maven 3.9.x until the Maven wrapper is generated and committed.
- Network access to Maven Central and `https://download.eclipse.org/releases/2026-03/`.

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

