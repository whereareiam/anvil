---
title: Lifecycle and ownership
description: Trace global registration, scoped preparation, process replacement, and ordered cleanup.
---

The engine owns global lifecycle and active contexts. Its constructor-supplied `ScenarioFactory`
creates a prepared `ScenarioContext` through scoped services, with startup and finalization
performed on that same context. The default launcher owns shared service instances and binds per-scenario
preparation, agents, processes, and players.

| Owner | Lifetime and responsibility |
|---|---|
| `EngineBuilder` and `EngineRegistration` | Install contributions and transfer shared resources before accepting scenarios |
| `AnvilEngine` | Validate scenario structure, retain contexts, attach extensions, run setup, and close remaining contexts |
| `LauncherAssembly` | Construct shared scoped services and the factory before engine construction; release services after scenarios and extension resources |
| `DefaultScenarioFactory` | Bind scoped platform, workspace, execution, protocol, and agent services for one run |
| Engine `ScenarioSession` | Retain a scoped context, install global extensions/setup once, and propagate final outcomes |
| Engine `RunningScenario` | Own the default context and close its players before finalizing its process group |
| `ManagedProcessGroup` | Own allocated targets, prepared process inputs, generation replacement, and ordered finalization |
| `PreparedProcess` | Retain process inputs across restarts and finalize after execution resources are released |
| `PreparedLaunch` | Supply one generation's final command and attach/detach its dependent services |
| `AgentSession` | Own one generation's agent credentials and connection behind a stable process client |
| Capability `ProcessCapabilities` | Own capabilities for one logical scenario process across generation replacement |
| `WorkspaceProvisioner` | Resolve workspace layouts, prepare directories, and apply run-directory retention policy |
| `ManagedProcess` | Supervise one generation's state, console, readiness, and termination |
| `ScenarioAttachment` | Own one global extension's per-scenario resources and receive the final outcome |

## Follow startup in dependency order

1. Assemble shared services, including the player service over every installed protocol library,
   construct the engine with its factory, and install global extensions.
2. Validate the scenario's global structure. The factory validates scoped declarations, resolves
   named artifacts, and negotiates forwarding through platform planning. Bind and validate compatible
   capability providers using each process's platform and available agent implementation.
3. Check the protocol library the scenario declares and validate the capability graph of every
   library its players select by default, before process startup. Allocate the complete execution
   topology and resolve process runtimes.
4. Prepare distributions and workspaces, restoring snapshots before installing assets. Independent
   preparation may run concurrently within the configured limits.
5. Return the prepared context with reusable inputs and allocated endpoints. No launch resources
   or generation-specific configuration are created until a process is started.
6. Start dependencies before dependents, with servers before their proxies. Each start configures
   its process and creates fresh launch resources, then launches and awaits readiness. Independent
   starts may overlap. Attach native agents after readiness and open the scenario's player manager.
7. After the context's complete startup reaches readiness, attach global scenario extensions and
   run the setup hook once. `engine.start(...)` returns to its caller only after this phase succeeds.

The default player service creates each protocol library lazily, when the first player selects it,
and reuses it for later scenarios until the engine closes. Library selection, the support policy,
native-version and authentication compatibility are checked for each requested player. A failed acquisition must release resources that were not transferred to another owner.

## Prepare an environment for individual startup

`ScenarioEngine.prepare(scenario, observer)` validates global structure and delegates to
`ScenarioFactory.create(...)`. Its result is a `ScenarioContext` owning the complete allocated
topology and prepared inputs. Preparation does not create process generations or run global
extensions and setup. Every scenario factory exposes that lifecycle; full-start entry points perform the
same preparation followed by `context.start()`.

The engine retains the prepared owner immediately so engine shutdown also releases environments
that never started. Individual `processes().start(name)` calls create only the selected generation;
`stop(name)` preserves that process's prepared inputs and address. `ScenarioContext.start()` starts
remaining processes in dependency order, then installs global attachments and executes setup once.
Later full starts restore stopped processes without repeating completed initialization.

Generation callbacks run before native launch and can run concurrently for independent processes.
The observer receives the same managed generation handle returned by process lookup. Launcher
composition supplies the logical process's core `CapabilityOwner` as an execution input; the managed
handle delegates capability lookup directly to that owner. Capabilities initialize after the process
first becomes ready. The observer must return promptly so startup can proceed.

## Identify process executions

A `PreparedProcess` survives restart, while each call to `launch()` configures a fresh generation.
The first start and every restart acquire these resources through the same path. An unstarted
context has no launch to close. This produces new agent credentials and attachments without repeating workspace restoration or
asset installation. Detach the old launch and stop its process before creating the replacement.

A running process handle describes one execution attempt; callers obtain the current handle after a
restart. Its immutable `executionId()` is an opaque UUID assigned by execution. Observations, snapshots,
and console cursors use that ID; it is neither an operating-system PID nor a restart counter. Both
executions expose the same process capability instances, which belong to the logical
process. Agent-backed capabilities use clients that follow its replacement connection; their
operations can be unavailable during restart. The launcher binds each generation's execution
callbacks to its `AgentSession`. Native player SDK handles retain their own connection generation
and must not silently rebind.

A restart or reconnect does not prove application persistence. Tests must verify the saved state.
Consumer guidance belongs under [Process actions](../../../building-blocks/environments/actions/index.md).

Structural validation and provisioning failures surface during preparation. Configuration and
launch failures surface during startup. A failed full startup finalizes the context, including
dependencies that already started; a failed individual start releases that attempt's launch and
retains prepared inputs until context finalization.

## Report outcomes and attempt every cleanup

`ScenarioContext.finish(successful)`, `ProcessGroup.finish(successful)`, and
`ScenarioAttachment.finish(successful)` carry the caller's outcome. Their default `close()` methods
report normal completion; earlier lifecycle failures still
prevent successful finalization. An embedding application that catches a failed journey should
call `context.finish(false)` before closing the engine.

Global attachments finish in reverse installation order before the underlying context is released.
Default scenario cleanup destroys players, closes process capabilities, detaches launch services,
and stops processes in reverse dependency order, stopping those that started in the same layer
together. Capability cleanup retains access to borrowed
agent clients until it completes. After the owner closes, capability lookup fails and
`hasCapability(...)` returns `false`. Execution targets and sessions close before prepared workspaces
are finalized.
Successful finalization saves enabled snapshots; failure policy controls retained workspaces.

Engine shutdown closes all remaining contexts before shared resources, in reverse ownership order.
If installation or startup fails, every transferred owner still receives cleanup, including prepared
components that never created a generation. Tooling finalizes failed lifecycle operations with
`finish(false)` and retains borrowed console handles for diagnostics after finalization. Preserve the
original failure and suppress later cleanup failures; one failing resource must not skip the rest.

JUnit invocation cleanup currently calls the context's normal `close()`. A test assertion by itself
is not passed to `finish(false)`, so it does not guarantee failed-workspace retention. Keep that
integration distinction explicit when changing or documenting outcome propagation.
