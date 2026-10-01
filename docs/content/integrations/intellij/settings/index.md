---
title: Preferences
description: Choose Anvil's default run view, failure handling, tab retention, command history, and scenario refresh behavior.
---

Open **Settings → Tools → Anvil** to customize the plugin. Changes take effect after **Apply** or
**OK**; **Cancel** leaves the previous preferences in place. These preferences apply across the IDE.
Remembered sections and command histories stay separate for each project.

| Setting                                  | Default             | Behavior                                                                                                                                                                           |
|------------------------------------------|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Expand scenarios automatically**       | Off                 | Expand scenario entries with child processes when the Scenarios view loads. The Environment sidebar always starts expanded.                                                        |
| **Refresh scenarios after project sync** | On                  | Prepare updated scenario definitions after a successful native project import, when no environment is active.                                                                      |
| **Default section for new runs**         | Last used           | Open Environment, Console, Players, or the last section you selected in that project. A project's first run opens Environment.                                                     |
| **Show Console when a run fails**        | On                  | Reveal the failed run's Console for startup or lifecycle failures and unexpected process exits.                                                                                    |
| **Keep completed successful tabs**       | 5                   | Close the oldest successful completed run tabs beyond this limit. Set 0 to retain all of them.                                                                                     |
| **Command history**                      | Current IDE session | Recall submitted console commands and successful non-sensitive action inputs while the project remains open. Choose Across IDE restarts to store history locally for each project. |
| **Global account directory**             | `~/.anvil/accounts` | Directory of accounts shared across projects. Each project chooses whether to include them in its [account manager](../accounts/index.md).                                  |

## Choose the view for new runs

Select **Console** as the default section when you mainly follow server startup and send commands.
Select **Environment** when you mainly inspect topology and start individual components. **Last used**
remembers your section choice within each project. Changing the preference affects new run tabs;
it does not replace the view you are currently reading.

Failure handling reacts to the run's lifecycle state. A server printing an error message does not
switch sections by itself. After Anvil reveals a failed run's Console, you can switch back to another
section without subsequent log messages pulling you away.

## Retain completed runs

The successful-tab limit applies after process cleanup has finished. Active and failed runs are
preserved. Closing an old successful tab releases its retained console output; the remaining tabs
keep their logs and details. This setting controls tabs in the current project session, rather than
restoring running environments after an IDE restart.

## Recall commands

Use the command input's history to choose a previous entry, then submit it explicitly. Histories are
separate for each scenario source, scenario, target, and command type: server console commands, player commands,
and player chat do not share a history. Common commands remain available across runs of the same
scenario in that project.

**Current IDE session** keeps commands in memory. **Across IDE restarts** saves them in the project's
local workspace settings. Switching back to the session option stops persistence and clears the
stored history while keeping the current in-memory entries available. The project's recent submissions
are bounded by IntelliJ's **Console commands history size** preference; each input shows only the entries
matching its own target and command type.

## Refresh after project changes

With automatic refresh enabled, a successful project sync prepares the selected scenario source's scenarios
again. Imports arriving while a run is active wait until cleanup finishes. Several imports during
that time result in one refresh of the latest model. Failed imports do not trigger a rebuild loop.
After you first open Anvil in a project, automatic refresh continues for that open project even when
the Anvil window is closed.

Turning this option off keeps initial discovery and explicit **Refresh scenarios** or **Sync project**
actions available. It does not watch every source edit; use **Refresh scenarios** after changing
definitions without syncing the project.

Server Java versions, memory, ports, distributions, and startup timeouts remain in scenario/project
definitions. See [engine configuration](../../../building-blocks/environments/configuration/engine/index.md)
for runtime settings.
