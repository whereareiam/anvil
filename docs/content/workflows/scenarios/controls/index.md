---
title: Runner controls
description: Inspect the active environment, send console commands, and control the foreground runner.
---

Enter these commands in the terminal after [starting a scenario](../running/index.md). They are runner
commands, not Gradle arguments or Minecraft chat commands. The command names are lowercase.

## Interactive commands

| Command | Effect |
|---|---|
| `status` | Print the active environment, entrypoint, and process addresses and states; report when nothing is running. |
| `list` | Show the selected group's members, or the selected provider's scenarios and groups. |
| `start <scenario>` | Close an active environment and start the named scenario; group sessions accept only members of that group. |
| `restart` | Close and start the entire current environment again. |
| `logs <process> [lines]` | Print a bounded console tail; defaults to 30 lines. |
| `send <process> <command>` | Send the rest of the line to that process's console. |
| `stop` | Close the current environment and keep the shell open. |
| `quit` / `exit` | Close the environment and leave the runner. |

`restart`, `logs`, and `send` require a running environment. After `stop`, use `start <scenario>`
before trying them. There is no shell command for stopping, starting, or restarting one individual
process. The Java API exposes a separate [per-process restart action](../../../building-blocks/environments/actions/restarts/index.md).

## Inspect a console

For the catalog example's server named `server`, enter:

```text
send server say scenario-console-check
logs server 50
```

The first command submits `say scenario-console-check` without a leading slash. Spaces after the
process name remain part of the command. The second prints up to fifty captured output lines.
Console capture is asynchronous, so a tail read immediately after submission can precede the response;
read it again when needed. Sending a command does not prove that the application finished its work.

Use the exact scenario process name shown by `status`. In a proxy environment, the proxy and lobby
have separate consoles. For a full log while its workspace exists, inspect the process's
`anvil-console.log`; [environment actions](../../../building-blocks/environments/actions/index.md)
cover console checkpoints and typed native operations from Java.

## Handle command failures

An unknown command prints a diagnostic and keeps the shell open. Invalid arguments, an unknown
process or scenario, or an execution failure can instead end the runner and trigger cleanup;
the shell does not currently recover from every command exception. Use a positive integer for the
optional log-line count and select names from `status` or `list`.

If startup or a command fails, inspect the first reported cause and the relevant
[troubleshooting guidance](../../../help/troubleshooting/index.md) before relaunching.
Keep workspace retention intentional when you need process files after the session ends.
