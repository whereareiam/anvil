---
title: Troubleshooting
description: Diagnose startup, provider discovery, artifact verification, and player-journey failures.
---

# Troubleshooting

Start with the first failure and the retained process workspace. A failed run normally keeps
`anvil-console.log` for each process and includes a bounded output tail in its exception.

| Symptom                                     | Check                                                                                          |
|---------------------------------------------|------------------------------------------------------------------------------------------------|
| EULA validation fails                       | Record acceptance explicitly with `anvil { acceptEula() }`                                     |
| No platform provider                        | Apply the unit plugin for every server/proxy platform in the scenario                          |
| No protocol provider or ambiguous selection | Add a provider to `anvilProtocols`; select its ID if several are installed                     |
| Missing capability or dependency            | Install its wiring artifact and a compatible adapter for the selected backend                  |
| Native version mismatch                     | Check every reachable server and `minecraftVersion` on local/named server artifacts            |
| Agent unavailable                           | Read that process's startup log; confirm the packaged matching agent JAR is present            |
| Unknown custom agent operation              | Install its extension JAR and service descriptor into the target process's extension directory |
| SHA-256 mismatch                            | Verify the selected artifact and pin; do not bypass the content check                          |
| Persistent workspace in use                 | Stop the existing owner before trying to reuse its directory                                   |

## Read the failure before retrying

Anvil's exception types identify the stage that failed. The general categories are in
`me.whereareiam.anvil.api.exception`; scenario-specific types are in its `scenario` subpackage.
Use the exception message, original cause, and retained logs together:

| Failure                       | What it means                                                      | Next step                                                                                     |
|-------------------------------|--------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `ScenarioValidationException` | The declaration cannot run as configured                           | Correct the named process, distribution, EULA, binding, or forwarding setting before retrying |
| `ProvisioningException`       | An artifact, Java installation, or workspace could not be prepared | Check the reported path or URL, access permissions, network response, and checksum            |
| `ProcessException`            | A process could not start, communicate, or stop                    | Use `getProcessName()` to find its workspace and read `anvil-console.log`                     |
| `ScenarioStartupException`    | A setup hook failed                                                | Use `getScenarioName()` and inspect `getCause()` to locate the failing setup operation        |

These categories extend `AnvilException`, so an embedding application can catch the base type for
common reporting. Platform, agent, and protocol implementations can also report their own exception
types. An assertion failure in a setup hook remains an assertion failure; cleanup still runs.

Do not turn an unexpected startup exception into a passing test. If you deliberately test a failing
restart, remember that the scenario remains unsuccessful for cache finalization even when you catch
the expected error.

### Check lookup and lifecycle errors

A `NoSuchElementException` from `processes().get()`, `server()`, or `proxy()` means the requested name
is absent from the scenario. Check the exception's list of available process names. An
`IllegalArgumentException` from a typed lookup means the name exists with the other role.

An `IllegalStateException` from restart means the scenario is closed. Perform restart inside the context's lifetime, and use the current scenario's process
accessor rather than retaining one after cleanup.

### Inspect cleanup failures

The original failure remains primary. Additional cleanup failures appear in the stack trace as
suppressed exceptions. Inspect them when a workspace stays locked or a process fails to stop; fixing
the test's first assertion will not necessarily fix a separate cleanup problem.

## Reproduce one failing journey

Run the smallest failing test in your consumer project:

```shell
./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest'
```

Replace the class name with your test. Keep its scenario declaration and version pins unchanged
while isolating the failure. For Anvil's own compatibility matrix, use the filters described in
[Contributing: testing](../../contributing/testing/index.md).

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
