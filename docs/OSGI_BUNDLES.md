# OSGi bundle inventory (Phase 6)

The update site bundles third-party JARs as Tycho-wrapped OSGi plugins (`wrapped.*` symbolic names). Use this doc when deciding which dependencies need owned manifests vs target-generated wrappers.

## Generate inventory

After `mvn verify`:

```powershell
.\scripts\inventory-p2-repository.ps1
```

To list wrapped Maven names from a Tycho resolve log:

```powershell
mvn -DskipTests package 2>&1 | Tee-Object build.log
.\scripts\list-wrapped-bundles.ps1 build.log
```

## Current policy (assistai-owned)

| Artifact | Approach | Notes |
|----------|----------|-------|
| OkHttp | **Owned wrapper** | `plugins/com.rubberjam.eclipse.assistai.osgi.okhttp` — explicit MANIFEST, no invalid `!kotlin.*` imports |
| Tomcat embed core | **Embedded in main** | `lib/tomcat-embed-core-*.jar` on main bundle classpath |
| MCP JSON Jackson2 | **Embedded in main** | `lib/mcp-json-jackson2-2.0.0-M2.jar` (ServiceLoader); `mcp-core`/`mcp` from target at 2.0.0-M2 |
| Spring AI / provider SDKs | **Target-generated** | `missingManifest=generate` on Maven target location |

## Next decisions (Phase 6)

1. Run `inventory-p2-repository.ps1` and archive output when cutting a release.
2. Compare wrapped Spring/MCP bundles against explicit roots in `releng/.../assistai.target`.
3. Add Tycho target filters only where duplicate package providers cause resolver conflicts.
4. Consider owned wrappers for Tomcat or MCP JSON if generated manifests need tightening.
