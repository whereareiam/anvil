---
title: Requirements
description: Declare what a scenario needs from the machine that runs it, so that a machine lacking it skips the test instead of failing it.
---

A scenario runs on a workbench: the place that holds its downloads, Java installations and workspaces and runs
its processes. Today that is always the machine the tests run on. Before Anvil plans anything of a scenario, it
checks what the scenario needs against what the workbench offers, and refuses it with every unmet requirement at
once. Nothing is downloaded or started for a refused scenario.

## Declare requirements

Most scenarios declare nothing. Declare a requirement only when the scenario depends on it.

In a scenario definition, import:

```java
import me.whereareiam.anvil.api.model.scenario.ScenarioRequirements;
```

Add the requirements to the scenario builder:

```java
.requirements(ScenarioRequirements.builder()
		.reach("sessionserver.mojang.com")
		.processors(8)
		.memoryMegabytes(8192)
		.build())
```

- `reach` names a host outside the workbench that the scenario's processes contact, such as Mojang's session
  server for a proxy in online mode: a host name without scheme or port, one call per host.
- `processors` and `memoryMegabytes` are what the scenario's processes need together; the machine must have at
  least that many processors and that much memory.

## Implied requirements

What the declaration already says is checked without being repeated. A scenario needs the execution provider each
of its processes runs on: servers and proxies the scenario's, or else the engine's default, and each container its
own when it names one. An installed provider that cannot run processes refuses the scenario with its reason; Docker
does when its daemon does not answer, for example on a machine without Docker.

A scenario of remotes alone runs no process and needs no execution provider.

## What the local workbench offers

The workbench in the engine's own JVM offers the machine's processors and physical memory, lets its processes
reach every host, and reports each installed execution provider that cannot run processes now.

## In JUnit

A test whose scenario is refused is skipped, reported as aborted with the reasons, such as:

```text
Workbench 'local' cannot run scenario 'cluster': execution provider 'docker' is unavailable: the Docker daemon
does not answer (...)
```

A run that must not pass with tests left out, such as one in CI, fails them instead:

```bash
./gradlew anvilTest -Danvil.requirements=strict
```

`anvil.requirements` is `skip`, the default, or `strict`. A foreground run or the IDE always fails a refused
scenario with the same reasons.
