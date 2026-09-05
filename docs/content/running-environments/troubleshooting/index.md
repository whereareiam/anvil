---
title: Troubleshooting
description: Diagnose startup, provider discovery, artifact verification, and player-journey failures.
---

# Troubleshooting

Start with the first failure and the retained process workspace. A failed run normally keeps
`anvil-console.log` for each process and includes a bounded output tail in its exception.

| Symptom | Check |
|---|---|
| EULA validation fails | Record acceptance explicitly with `anvil { acceptEula() }` |
| No platform provider | Apply the unit plugin for every server/proxy platform in the scenario |
| No protocol provider or ambiguous selection | Add a provider to `anvilProtocols`; select its ID if several are installed |
| Missing capability or dependency | Install its wiring artifact and a compatible adapter for the selected backend |
| Native version mismatch | Check every reachable server and `minecraftVersion` on local/named server artifacts |
| Agent unavailable | Read that process's startup log; confirm the packaged matching agent JAR is present |
| Unknown custom agent operation | Install its extension JAR and service descriptor into the target process's extension directory |
| SHA-256 mismatch | Verify the selected artifact and pin; do not bypass the content check |
| Persistent workspace in use | Stop the existing owner before trying to reuse its directory |

## Isolate a server test

For framework-maintainer tests, narrow the class and then the compatibility matrix:

```shell
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*spigot.*'
```

`anvilMatrixFilter` affects compatibility cases only. It does not filter every live test. For a
consumer project, use `anvilTest --tests 'com.example.test.PlayerJoinTest'`.

## Configuration and artifacts

YAML/TOML writers preserve unrelated data but rewrite formatting and comments. Resolve malformed
files and duplicate YAML keys before retrying. Runtime-owned listener and forwarding settings must
not be overridden by proxy `.setting(...)` entries. Velocity strings require TOML quotes.

Use the artifact registry rather than a hard-coded sibling build path. If local snapshot artifacts
are out of sync, rebuild/publish the relevant Anvil artifacts and retry with matching versions.
See [building and publication](../../contributing/publishing/index.md).

## Report a reproducible failure

Include the selected Anvil version, scenario declaration, server/proxy versions and pins, native
client version, failing assertion, and relevant console tails. Remove credentials and private
account-store contents. The repository's [issue tracker](https://github.com/whereareiam/anvil/issues)
is the place to report a framework defect.
