---
title: Inspecting a run
description: Use a run's Environment, Console, and Players views to control processes, send commands, and invoke actions.
---

Each run opens in its own session tab, which owns that environment, its state, and its console.
One environment can be active per IntelliJ project. The status badge at the left of the tab
summarizes the whole environment, including partial startup and pending setup. A green **Running**
badge means the processes are ready; it is not a test result.

| View | Use it to |
|---|---|
| **Environment** | Inspect configuration and live state; start, stop, or restart the scenario or single processes |
| **Console** | Read merged or per-process output and send console commands to one process |
| **Players** | Inspect the players the scenario created and invoke their actions |

[Preferences](../settings/index.md) choose which view a new run opens and whether a failed run
switches to its Console.

## Control processes

Select the scenario root or a process in the **Environment** sidebar and use its toolbar:

| Action | Selection | Effect |
|---|---|---|
| **Start scenario** | Scenario root | Starts the remaining processes and runs setup if it has not run yet |
| **Start server** / **Start proxy** | Process | Starts that process in the prepared environment |
| **Stop process** | Process | Stops that process and keeps its address and workspace |
| **Restart process** | Running process | Restarts the process with its workspace; other processes keep running |
| **Stop scenario** | Scenario root | Stops every process and cleans up the environment, keeping its output |

Starting a single process prepares the complete environment, including listener addresses and
routes, but leaves the other processes stopped. A proxy can start while its backends are stopped,
but cannot route players to them until they start. Stopping a process and starting it again does not
rerun setup.

Players may need to reconnect after a restart, and restarting does not rerun setup; see
[process restarts](../../../building-blocks/environments/actions/restarts/index.md). If a process fails
to start, stop, or restart, the run is marked failed and cleaned up. Inspect the retained console,
fix the cause, and start a new run.

The details panel shows each process's live state, address, and workspace. Select **Workspace** to
open the process directory.

## Read output and send commands

The **Console** sidebar lists **All processes** and each server or proxy. Selecting a process filters
the output to it and makes it the command target; with **All processes**, choose a target in the
command bar first. Type a command and press Enter or select **Send**. Preparation messages and
diagnostics stay visible under every filter, and Java exception links navigate to project sources.

The console keeps a bounded tail of recent output. IDE runs request ANSI colors from the platforms:
Paper and Velocity through TerminalConsoleAppender, and Spigot and BungeeCord through JLine. To keep
each platform's own color detection, set `anvil.console.colors` to `false` in the project's
`anvil { engine { ... } }` settings; see [engine properties](../../../building-blocks/environments/configuration/engine/index.md).
Explicit JVM arguments on a server or proxy override the provider defaults, and custom platform
providers supply their own color support.

## Invoke actions

Capabilities and [tooling extensions](../../../extending/tooling/index.md) contribute actions and
observations for scenarios, processes, and players. Select a target in **Environment** or a player in
**Players**, choose an action, and select **Open action…**. Fill in the generated form and select
**Run**; the result appears as a message or table in the same dialog. The runtime checks availability
again when the action runs, so a stale selection cannot bypass the current state.

The standard Gradle scenarios workflow includes the built-in tooling bundle. Players with the
Messages capability offer **Player command** and **Chat message**, and the Session capability
contributes a connection observation.

| Target | Example | What to check |
|---|---|---|
| Paper console | `say Maintenance rehearsal` | Server output and plugin behavior |
| Alice, Player command | `myplugin status` | The plugin's response to that player |
| Alice, Chat message | `hello` | Native chat handling |
| External capability | Its action and generated form | Returned values and contributed observations |

Players appear once the scenario creates them, for example in its
[setup hook](../../../building-blocks/environments/definitions/index.md#prepare-live-state). An
acknowledged submission does not prove the in-game effect; check observations or logs. Sensitive
action inputs are excluded from history and saved drafts.

## Close and retain runs

Completed tabs keep their output and details until you close them. By default the five most recent
successful tabs are kept and failed runs stay until closed. Closing an active tab stops its
environment, and an old tab cannot control a newer run. After changing scenarios, dependencies, or
artifacts, stop the run and refresh the scenarios to prepare the updated runtime.
