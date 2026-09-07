---
title: Lifecycle and ownership
description: Trace resource acquisition, process replacement, and cleanup across a scenario run.
---

Lifecycle changes must preserve the owner of every resource. The engine owns the shared protocol
backend and artifact store; each scenario owns its execution session, process slots, players, agent
connections, and workspaces.

| Owner | Lifetime and responsibility |
|---|---|
| `AnvilEngine` | Retains started sessions and closes them before the shared backend and artifact store |
| `ScenarioSession` | Exposes one context and runs setup after its resources are ready |
| `SessionResources` | Acquires the scenario graph and releases it after success or failure |
| `ProcessSlot` | Retains a process declaration and prepared workspace across restarts |
| `ManagedProcess` | Supervises one execution generation's state, output, readiness, and termination |

## Follow startup in dependency order

1. Select the installed protocol provider and compatible capability composer during engine construction.
2. Resolve named artifacts and validate scenario structure, distribution selectors, and forwarding.
3. Open the execution session, resolve Java requirements, and prepare process targets and addresses.
4. Prepare distributions, assets, caches, and workspaces. Independent work can use configured limits.
5. Configure processes in declaration order with allocated runtime values.
6. Start servers and wait for readiness, then start proxies. Initialize player services and execute
   the scenario setup hook.
7. Retain the successful session in the engine.

The protocol backend is created lazily when the scenario acquires its resources. A startup or setup
failure rolls back acquired resources and preserves cleanup failures as suppressed exceptions.

Provider configuration remains deterministic even when preparation and startup use concurrency.
Address translation and enforcement of network policy belong to the execution session; Java
provisioning supplies local installations and verified archives while Docker owns image mappings.

## Preserve restart semantics

A process slot keeps its workspace and declaration while replacing the running process generation.
The public running handle describes a generation; callers obtain the current process after a
restart. Scenario-owned agent handles follow the replacement connection.

Do not infer application persistence from a successful restart or reconnect. A test must verify the
application's saved state. Consumer guidance belongs under
[Processes](../../../building-blocks/environments/actions/index.md).

## Attempt every cleanup

Scenario cleanup destroys players first, then closes agent connections, stops processes in reverse
order, finalizes process workspaces, closes the execution session, and handles the run directory.
Engine cleanup closes retained sessions before its shared backend and artifact store.

Independent cleanup failures must not skip later resources. Preserve the first failure and attach
later ones as suppressed exceptions. Keep bounded output tails and retained workspaces for recorded
lifecycle failures when `keepFailedWorkspaces` is enabled.

The session's failure bookkeeping includes startup, restart, and cleanup failures. A JUnit assertion
failure by itself is not currently passed into this lifecycle success flag, so it does not guarantee
workspace retention. Preserve that distinction when documenting or changing cleanup integration.
