---
title: Lifecycle and ownership
description: Trace global registration, scoped preparation, process replacement, and ordered cleanup.
---

The engine owns global lifecycle and active contexts. Its registered `ScenarioExecutor` acquires a
ready context through scoped services. The default launcher assembly owns shared service instances
and binds per-scenario preparation, agents, processes, and players.

| Owner | Lifetime and responsibility |
|---|---|
| `EngineBuilder` and `EngineRegistration` | Install contributions and transfer shared resources before accepting scenarios |
| `AnvilEngine` | Validate scenario structure, retain contexts, attach extensions, run setup, and close remaining contexts |
| `LauncherEngineExtension` | Construct shared scoped services and register the scenario executor with the engine |
| `ScenarioLauncher` | Bind scoped platform, workspace, execution, protocol, and agent services for one run |
| Engine `RunningScenario` | Own the ready context and close its players before finalizing processes |
| `ManagedProcessGroup` | Own allocated targets, prepared process inputs, generation replacement, and ordered finalization |
| `PreparedProcess` | Retain process inputs across restarts and finalize after execution resources are released |
| `PreparedLaunch` | Supply one generation's final command and attach/detach its dependent services |
| `AgentSession` | Own one generation's agent credentials and connection behind a stable process client |
| Capability `ProcessCapabilities` | Own capabilities for one logical scenario process across generation replacement |
| `WorkspaceProvisioner` | Resolve workspace layouts, prepare directories, and apply run-directory retention policy |
| `ManagedProcess` | Supervise one generation's state, console, readiness, and termination |
| `ScenarioAttachment` | Own one global extension's per-scenario resources and receive the final outcome |

## Follow startup in dependency order

1. Build the engine, install global extensions, and select the default assembly's protocol provider.
2. Validate the scenario's global structure. The executor validates scoped declarations, resolves
   named artifacts, and negotiates forwarding through platform planning. Bind and validate compatible
   capability providers using each process's platform and available agent implementation.
3. Initialize the selected protocol backend before process startup. Allocate the complete execution
   topology and resolve process runtimes.
4. Prepare distributions and workspaces, restoring snapshots before installing assets. Independent
   preparation may run concurrently within the configured limits.
5. Configure every generation in declaration order using the allocated runtime values.
6. Start dependencies before dependents, with servers before their proxies. Attach native agents
   after readiness and open the scenario's player manager.
7. Return the ready context to the engine, attach global scenario extensions, and run the setup hook.
   Retain the context only after startup succeeds.

The default player service creates its backend lazily for the first scenario and reuses it for
later scenarios. Native-version and authentication compatibility are checked for each requested
player. A failed acquisition must release resources that were not transferred to another owner.

## Preserve generation identity

A `PreparedProcess` survives restart, while each call to `launch()` configures a fresh generation.
This produces new agent credentials and attachments without repeating workspace restoration or
asset installation. Detach the old launch and stop its process before creating the replacement.

A running process handle describes one generation; callers obtain the current handle after a
restart. Both generations expose the same process capability instances, which belong to the logical
process. Agent-backed capabilities use clients that follow its replacement connection; their
operations can be unavailable during restart. The launcher binds each generation's execution
callbacks to its `AgentSession`. Native player SDK handles retain their own connection generation
and must not silently rebind.

A restart or reconnect does not prove application persistence. Tests must verify the saved state.
Consumer guidance belongs under [Process actions](../../../building-blocks/environments/actions/index.md).

## Report outcomes and attempt every cleanup

`ScenarioContext.finish(successful)` and `ScenarioAttachment.finish(successful)` carry the caller's
outcome. Their default `close()` methods report normal completion; earlier lifecycle failures still
prevent successful finalization. An embedding application that catches a failed journey should
call `context.finish(false)` before closing the engine.

Global attachments finish in reverse installation order before the underlying context is released.
Default scenario cleanup destroys players, closes process capabilities, detaches launch services,
and stops processes in reverse dependency order. Capability cleanup retains access to borrowed
agent clients until it completes. After the owner closes, capability lookup fails and
`hasCapability(...)` returns `false`. Execution targets and sessions close before prepared workspaces
are finalized.
Successful finalization saves enabled snapshots; failure policy controls retained workspaces.

Engine shutdown closes all remaining contexts before shared resources, in reverse ownership order.
If installation or startup fails, every transferred owner still receives cleanup. Preserve the
original failure and suppress later cleanup failures; one failing resource must not skip the rest.

JUnit invocation cleanup currently calls the context's normal `close()`. A test assertion by itself
is not passed to `finish(false)`, so it does not guarantee failed-workspace retention. Keep that
integration distinction explicit when changing or documenting outcome propagation.
