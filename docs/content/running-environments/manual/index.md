---
title: Manual environments
description: Discover scenario catalogs and keep a local environment open for a developer to join.
---

# Manual environments

The foreground runner uses the same scenario and platform contracts as automated tests. Apply
`me.whereareiam.anvil.scenarios` or the umbrella plugin, and register your catalog:

```kotlin
anvil {
    scenarioProviders.add("com.example.test.DevelopmentScenarios")
}
```

The catalog class implements `AnvilScenarioProvider` and registers scenarios with `ScenarioRegistry`.
Use `.manual(true)` on environments intended for manual operation. Named `ScenarioGroup` values
collect scenarios that the interactive shell can switch between.

```shell
./gradlew anvilScenario --list
./gradlew anvilScenario --scenario=local-paper
./gradlew anvilScenario --group=development
```

If several catalog providers are configured, select one with
`--provider=com.example.test.DevelopmentScenarios`. Listing can inspect multiple configured catalogs;
starting a scenario or group requires one selected provider.

## Interactive commands

| Command | Behavior |
|---|---|
| `status` | Print running process addresses and state |
| `list` | List available scenarios, or the selected group's members |
| `start <scenario>` | Replace the current environment |
| `restart` | Restart the current scenario |
| `logs <process> [lines]` | Show a bounded console tail |
| `send <process> <command>` | Send a process console command |
| `stop` | Stop the current environment and retain the shell |
| `quit` or `exit` | Close the environment and leave the runner |

## Joining from another machine

Loopback is the default game-listener address. A non-loopback listener requires both
`.manual(true)` and `.allowLanBinding(true)` on the scenario, plus an explicit `.bindAddress(...)`.
Agent endpoints remain authenticated and loopback-only even when a manual game listener is exposed.

Manual mode does not accept the EULA for you, remove authentication requirements, or translate client
versions. Choose a vanilla client matching the scenario's server version.
