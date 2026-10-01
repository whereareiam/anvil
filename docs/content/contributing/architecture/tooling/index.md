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
| `anvil-integration/integration-intellij/intellij-api`      | IntelliJ integration contracts                                                                 |
| `anvil-integration/integration-intellij/intellij-engine`   | IntelliJ integration lifecycle and tooling logic                                               |
| `anvil-integration/integration-intellij/intellij-gradle`   | Native Gradle adapter                                                                          |
| `anvil-integration/integration-intellij/intellij-ui`       | IntelliJ SDK UI and presentation                                                               |
| `anvil-integration/integration-intellij/intellij`          | Plugin descriptor and packaged assembly                                                        |

## Bind the IntelliJ integration

`intellij-api` defines scenario-source discovery and preparation, a scenario catalog, environment
lifecycle and retained handles, a project account library, preferences, project command history, and
read-only session logs. UI code retrieves those typed services from the IntelliJ container. Its
dialogs own their disposable sign-in operations, and environment views release their handles when
closed. Engine-owned XML state beans are translated to immutable preference snapshots at the boundary.

`SourceDiscovery`, implemented by `ProjectSourceDiscovery`, owns source selection, native
sync, and refresh after successful imports. Initialization begins when Anvil is first
opened in a project. Observation continues until the project closes, independently of view subscribers.
Refresh coalesces imported revisions and waits for preparation and environment cleanup. Views read
`DiscoverySnapshot` and submit selection, refresh, or sync commands through the contract.

The catalog panel composes Swing controls. Its `CatalogController` owns view interactions and
subscriptions, and releases those subscriptions when the panel closes. Settings likewise separate
the editable form from the native Apply/Reset adapter.

The `intellij` descriptor binds engine implementations to those interfaces. `ProjectScenarioCatalog`
owns the selected source's discovered definitions, loading state, and discovery output.
`ProjectEnvironmentLifecycle` owns the active environment and retained handles. Catalog state remains
available after an environment fails; each `EnvironmentExecution` owns its own authoritative snapshot.

Both services use `ProjectToolingHost` to share one tooling launch. Discovery can prepare a source
before the user selects a scenario; starting an environment reserves that same launch, including while
preparation is still in progress. Refresh can replace an idle launch. `ToolingLaunch` owns preparation,
the runner, the manifest, and temporary account files. Its private resource owner registers the manifest
and account workspace as they are acquired, including during partial startup. `ToolingConnection`
reads process output and waits for diagnostic delivery after termination. The resource scope then
releases accounts and the manifest, preserving the first failure and suppressing additional cleanup
failures. Replacement starts only after this cleanup has completed.

Launch scheduling, cancellation, execution, and cleanup settle through one terminal-completion path.
A failing listener cannot prevent other listeners, pending futures, or owned resources from being
settled. Readiness and discovery callbacks remain separate from resource acquisition and release.

A `RetainedEnvironmentSession` stays bound to its original execution. Stopped handles retain their
state and output, and their controls cannot reach a newer environment. Closing a handle removes it
from the retained list, but the project remains busy until its execution finishes cleanup. Session
controls report transport submission asynchronously; contributed actions return their remote results.

`ToolingClient.request(operation, payload)` serializes writes, correlates replies, and completes
pending results when the connection closes or fails. Each pending request retains the operation's
response type, including collection element types. `ToolingMessageCodec` maps typed envelopes and shared payload models through Jackson, and validates
protocol compatibility before delivering events and results. `ToolingOperationRegistry` binds shared operation declarations to typed session handlers and their
cancellation policy. `ToolingRequestReader` decodes each registered request type generically, while
`ToolingProtocolWriter` serializes typed response and event envelopes. Wire operations and envelopes stay inside the protocol implementations. UI controls use explicit environment methods such as `restartProcess` and `console`.

`BuildIntegration` is the optional native build-system extension point, implemented by
`intellij-gradle`. Gradle SDK classes stay within that adapter. Engine output remains a neutral retained
log; catalog state is one `CatalogSnapshot` with a `CatalogState`, and environment labels derive from `SessionSnapshot`
in the UI. The UI creates and owns native console presentations. Engine and UI communicate through
contracts, with service and extension wiring in the assembly.

Production descriptor bindings are tested in `intellij`. Engine and UI tests install their own
contract bindings, and API contract tests live in `intellij-engine`.

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
