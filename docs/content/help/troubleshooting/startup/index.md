---
title: Startup and provisioning
description: Resolve preflight, distribution, Java, and process-readiness failures.
---

If no process starts, check validation and provisioning first. If a process starts and then times
out, inspect its `anvil-console.log` before raising the readiness deadline.

## Before launch

| Symptom                                               | Concrete next check                                                                           |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| EULA acceptance is missing                            | Record acceptance through `anvil { acceptEula() }` in the project running the scenario        |
| Platform provider is unavailable                      | Apply a unit for every server and proxy platform used in the scenario                         |
| No protocol library is installed                      | Add the library dependency, such as `protocol-mcprotocol`, to `anvilRuntimeOnly`              |
| A scenario or engine selects an unknown protocol library | Use one of the installed library IDs that the message lists                                |
| A warning says a scenario cannot create players for a server | The server's default release cannot be launched yet; see [player creation](../players/index.md#check-the-protocol-library) |
| Local or named server has no native version           | Set the server declaration's `minecraftVersion`                                               |
| Mutable distribution rejected                         | Select an explicit provider build or content checksum for automated runs                      |
| Checksum mismatch                                     | Check the chosen source and expected pin; do not replace the pin merely to silence validation |
| Artifact resolves to zero or multiple files           | Register the exact producer output and check transitive artifact notation                     |
| Two processes resolve to the same workspace directory | Choose distinct directory-safe names: `server/a` and `server?a` both become `server-a`        |
| Offline artifact/metadata is missing                  | Prepare the exact inputs with downloads enabled, then retry offline                           |

The complete setup is in [Installation](../../../getting-started/installation/index.md),
[distribution selection](../../../building-blocks/environments/provisioning/platform/index.md), and
[Gradle artifact registration](../../../integrations/gradle/dsl/index.md).

## Java and execution

The JVM running Gradle and the JVM running a server can be different installations. Each process
runs on exactly one LTS release: the one it requests, or its platform version's preferred LTS. Check
engine, scenario, and process overrides when planning refuses a version. If downloads are disabled,
supply that exact version through `JAVA_<feature>_HOME` or an explicit source. Archive sources need
both a URI and SHA-256.

Paper `1.16.5` and `1.17.x` print `Unsupported Java detected` when they run above their maximum with
`-DPaper.IgnoreJavaVersion=true`, which Anvil adds only for an explicitly requested newer Java. The
line is expected; see [Java selection](../../../building-blocks/environments/provisioning/java/index.md#run-above-a-platforms-maximum).

Docker needs an available local daemon and explicit provider image mappings for the requested
Java selections. Setting only `anvil.execution=docker` does not supply those mappings. Use the
[execution guide](../../../building-blocks/environments/configuration/execution/index.md) for supported configuration.

## After a process starts

Look for the earliest platform error: a missing plugin dependency, malformed configuration,
unsupported Java bytecode, or an occupied listener port can all appear later as a readiness timeout.
Candidate host ports are selected before launch but are not reserved sockets held for the child JVM.

If an agent is unavailable, confirm that the matching platform-agent artifact is on the runtime
classpath and that the platform loaded it. Platform units normally supply the pair. Embedded
applications must supply those artifacts themselves; selecting a provider ID cannot repair a
missing runtime dependency.

Anvil's YAML/TOML writers preserve unrelated values while replacing runtime-owned listener and
forwarding settings. They can rewrite formatting and comments. Fix malformed files and duplicate
YAML keys before retrying. Velocity custom settings use TOML literals, including quotes around
strings. See [Velocity](../../../building-blocks/environments/platforms/proxies/velocity/index.md).

Raise the scenario's `startupTimeout` only after confirming startup is progressing correctly on a
slow machine. The process shutdown grace and player observation timeouts are separate settings.
