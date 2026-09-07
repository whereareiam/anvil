---
title: Running scenarios
description: List prepared environments, launch a scenario or group, and replace the active environment.
---

Launch a prepared environment from the Gradle project containing its catalog. The foreground task
keeps one environment running while you use its shell and Minecraft client.
Complete [catalog registration](../catalogs/index.md) before following these commands.

## Start one environment

The example catalog registers `local-paper`. From the project root, run:

```shell
./gradlew anvilScenario --scenario=local-paper --console=plain
```

Anvil prepares the declared inputs, starts the processes and agents, and executes the setup hook.
When ready, the terminal prints the environment name, an entrypoint after `Join:`, process addresses,
and the available shell commands. Follow [joining an environment](../joining/index.md) to connect.

Use `./gradlew anvilScenario --list --console=plain` to check names without starting an environment.
Do not combine `--list` with a scenario or group selection. Starting requires exactly one of
`--scenario` and `--group`.

## Open a group

```shell
./gradlew anvilScenario --group=development --console=plain
```

The runner starts the group's first registered member. In its shell, `list` shows the allowed member
names. Use `start <scenario>` to replace the current environment with one of those members.
For a session launched without a group, `list` shows the selected provider's catalog and `start` can
select any registered scenario in that catalog.

The example group contains only `local-paper`; add more members in Java when you want to switch
between different prepared setups. The group stays selected for the lifetime of that runner session.

## Select a provider when needed

With exactly one configured provider, selection is automatic. If several are registered, choose one:

```shell
./gradlew anvilScenario --provider=com.example.test.DevelopmentScenarios --scenario=local-paper --console=plain
```

An unqualified `--list` can list all configured providers, but a running session uses one selected
provider. Change providers by leaving the runner and launching another session with the desired class.

## Replace or stop the environment

Enter these commands in the running shell, without `./gradlew`:

- `restart` closes the current environment and starts its declaration again.
- `stop` closes the current environment and leaves the shell open.
- `start local-paper` starts that registered environment, closing an active one first.
- `quit` or `exit` closes the environment and leaves the runner.

A whole-environment restart starts new processes, runs workspace preparation, and repeats the setup
hook. Human clients must reconnect to the printed address. Files survive according to the declared
[workspace policy](../../../building-blocks/environments/workspaces/index.md); manual mode does not
make a fresh workspace persistent.

The shell's `restart` differs from the Java
[per-process restart action](../../../building-blocks/environments/actions/restarts/index.md), which
replaces only one process inside an open environment.
For command syntax, logs, and error behavior, see [runner controls](../controls/index.md).
