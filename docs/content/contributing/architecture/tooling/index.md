---
title: Project tooling
description: Trace project declarations, prepared runtimes, and session ownership across build tools and IDE clients.
---

Project tooling connects a build's declared scenario runtime to clients such as IntelliJ IDEA.
Importing a project and running its code are separate operations: sync supplies a declaration,
while preparation compiles and resolves the inputs needed by a tooling JVM.

```text
IDE sync → build integration → project declaration → imported module data
Definition loading → declared preparation action → prepared launch → tooling runner
Run scenario → RunnerSession → global engine → launcher ScenarioFactory.create → prepared topology
Run all → ScenarioContext.start() → ready processes → global extensions and scenario setup
```

## Keep the contracts separate from their adapters

| Owner                                                      | Responsibility                                                                                 |
|------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `anvil-integration/integration-gradle/gradle-artifacts`    | Tracked artifact file inputs and JVM property binding                                          |
| `anvil-integration/integration-gradle/gradle-tooling`      | Native Gradle task discovery, definition scanning, and deferred runtime export                 |
| `anvil-tooling/tooling-api`                                | Portable scenario descriptors, sessions, actions, observations, and logs                       |
| `anvil-tooling/tooling-extension-api`                      | Host-side contributions using core scenario/process/player views                               |
| `anvil-tooling/tooling-builtin`                            | Optional actions and observations for built-in capabilities                                    |
| `anvil-tooling/tooling-runner`                             | Definition discovery, sessions supplied with an engine factory, terminal commands and protocol |
| `anvil-tooling/tooling-launcher`                           | Executable CLI/IDE entry points, engine property parsing and default engine assembly           |
| `anvil-integration/integration-gradle/gradle-base`         | Shared source-set setup, engine inputs, and internal plugin state                              |
| `anvil-integration/integration-gradle/gradle-dsl`          | Public Gradle DSL facade composed from the base and artifact integrations                      |
| `anvil-integration/integration-gradle/gradle-artifacts`    | Named artifact registration, resolution, and task input wiring                                 |
| `anvil-integration/integration-gradle/gradle-plugin`       | Assemble foreground execution and project discovery under `me.whereareiam.anvil`               |
| `anvil-integration/integration-gradle/gradle-junit`        | Assemble Gradle testing and the independent JUnit runtime                                      |
| `anvil-integration/integration-gradle/gradle-platforms`    | Gradle adapters for platform providers                                                         |
| `anvil-integration/integration-gradle/gradle-capabilities` | Gradle adapters for capability providers                                                       |
| `anvil-integration/integration-intellij`                   | IntelliJ plugin assembly composing the feature modules                                         |
| `anvil-integration/integration-intellij/intellij-api`      | Contracts the IntelliJ features and build integrations share                                   |
| `anvil-integration/integration-intellij/intellij-common`   | Standard implementations of the shared IntelliJ services                                       |
| `anvil-integration/integration-intellij/intellij-scenario` | Scenario catalog, tooling launches, environments, consoles, and run configurations             |
| `anvil-integration/integration-intellij/intellij-account`  | Accounts, pools, sign-in, and the accounts a run is given                                      |
| `anvil-integration/integration-intellij/intellij-gradle`   | Native Gradle adapter                                                                          |

## Bind the IntelliJ integration

The plugin's modules and how they reach each other are described in
[the module architecture](../modules/index.md#intellij-plugin-modules). This section follows one run
through the scenario module's services.

`SourceDiscovery` owns source selection, native sync, and reloading after successful imports.
Initialization begins when Anvil is first opened in a project. Observation continues until the project
closes, independently of open views. Reloads coalesce imported revisions and wait for preparation and
environment cleanup. Views read `SourceDiscovery.Snapshot` and submit selection, reload, or sync commands.

`ScenarioCatalog` owns the selected source's discovered definitions, loading state, and preparation
output in one `ScenarioCatalog.Snapshot`. `EnvironmentLifecycle` owns the active environment and the
retained sessions. Catalog state remains available after an environment fails; each
`EnvironmentExecution` owns its own authoritative snapshot.

Both services share one tooling launch through `ToolingHost`. Discovery can prepare a source before the
user selects a scenario; starting an environment reserves that same launch, including while preparation
is still in progress. Reloading can replace an idle launch. `ToolingLaunch` owns preparation, the runner,
the manifest, and the staged accounts it obtains from the `AccountStaging` service. Its private resource
owner registers the manifest and the staged accounts as they are acquired, including during partial
startup. `ToolingConnection` reads process output and waits for diagnostic delivery after termination.
The resource scope then releases the accounts and the manifest, preserving the first failure and
suppressing additional cleanup failures. A replacement starts only after this cleanup has completed.

Launch scheduling, cancellation, execution, and cleanup settle through one terminal-completion path.
A failing listener cannot prevent other listeners, pending futures, or owned resources from being
settled. Readiness and discovery callbacks remain separate from resource acquisition and release.

An `EnvironmentSession` stays bound to its original execution. Stopped sessions retain their state and
output, and their controls cannot reach a newer environment. Closing a session's tab removes it from
the retained list, but the project remains busy until its execution finishes cleanup. Session controls
report transport submission asynchronously; contributed actions return their remote results.

`ToolingClient.request(operation, payload)` serializes writes, correlates replies, and completes
pending results when the connection closes or fails. Each pending request retains the operation's
response type, including collection element types. `ToolingMessageCodec` maps typed envelopes and
shared payload models through Jackson, and validates protocol compatibility before delivering events
and results. `ToolingOperationRegistry` binds shared operation declarations to typed session handlers
and their cancellation policy. `ToolingRequestReader` decodes each registered request type generically,
while `ToolingProtocolWriter` serializes typed response and event envelopes. Wire operations and
envelopes stay inside the protocol implementations; views call explicit session methods such as
`restartProcess` and `console`.

`BuildIntegration` is the native build-system extension point, implemented by `intellij-gradle`. Gradle
SDK classes stay within that adapter. Output remains a neutral retained `SessionLog`; every view
creates its own `ConsolePresentation` over it.

Each module's tests load that module's real descriptor fragment through a test-only `plugin.xml`, with
stand-ins for contracts other modules implement; the plugin's own tests check how the fragments compose.

## Exchange protocol messages

The runner and IDE use protocol version **7**. The ready message advertises the runner's version;
the IDE requires an exact match before submitting requests. Update the project's Anvil artifacts and
the IDE plugin together when this version changes. Version 6 peers use a different action-target shape
and are rejected by the version check.

Portable payloads such as `ScenarioDescriptor`, `SessionSnapshot`, `LogEvent`, `ActionRequest`, and
`ActionResult` live in `tooling-api`. Their immutable Lombok builders carry Jackson binding metadata,
so both peers use the model's field definitions and defaults. Mapper configuration, transport headers,
request correlation, and dispatch remain in the implementations. Missing required model values and
invalid enums or identifiers fail decoding; protocol mappers reject scalar type coercion.

Predefined operation contracts live at the `tooling-api` root. Each `ToolingOperation<Q, R>` declares one stable
name, a request class, and a response type reference. A `Void` request type uses null and emits only the
transport header. Payload models contain operation arguments; request IDs and operation names belong
to the transport envelope.

| Contract group | Operations |
|---|---|
| `ScenarioOperations` | Discover definitions and start a selected scenario. |
| `EnvironmentOperations` | Read snapshots, start remaining processes, stop the environment, and invoke contributed actions. |
| `ProcessOperations` | Start, stop, or restart a process, and submit console commands. |

For a connected IntelliJ tooling client, discovery returns its declared collection type:

```java
CompletableFuture<List<ScenarioDescriptor>> definitions =
        client.request(ScenarioOperations.DISCOVER, null).result();
```

The exchange also exposes `submitted()`, which reports transport acceptance independently of remote
completion. Adding an operation requires a shared declaration and a typed runner handler; request and
response mapping use the generic transport path. Runner registration determines whether an operation
can be interrupted or cancels pending work. Protocol framing, resource ownership, and cancellation
remain outside the shared API.

An action request contains its target as the shared `ActionTarget` object:

```json
{
  "id": "request-1",
  "operation": "action",
  "sessionId": "session-1",
  "actionId": "example.inspect",
  "target": { "type": "PROCESS", "name": "lobby" },
  "arguments": {}
}
```

The response echoes the request ID. The pending request supplies the expected result type, so the
client can decode the response payload into a shared model after correlation. Round-trip tests run
the actual runner protocol against the IDE codec, covering nested targets, results, logs, and rejected
malformed requests.
