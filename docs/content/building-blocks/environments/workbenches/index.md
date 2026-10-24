---
title: Workbenches
description: Run a scenario's servers, proxies, containers and players on another machine, such as a PC with more memory, while the tests run here.
---

A workbench is the place a scenario's processes run: it holds their downloads, Java installations and
workspaces, and runs the servers, proxies, containers and simulated players. Unless you choose another one, that
is the machine the tests run on. A workbench on another machine takes over all of it, while the tests, the
build and the debugger in your IDE stay on your machine. Only what the tests send and observe crosses the
network: commands, chat messages, console lines, the files tests read and write, and plugin JARs that changed.
Downloads happen on the workbench, and the players' game traffic never leaves it.

## Set up a workbench

The workbench is one JAR, `anvil-workbench.jar`, built with `./gradlew :anvil-workbench:workbench-server:shadowJar`.
It needs Java 25 and, for the default policy, Docker. On the workbench's machine:

```bash
java -jar anvil-workbench.jar run --data /srv/anvil-workbench
```

It listens on port 7400, keeps everything in its data directory, and prints a one-time pairing code the first
time. `pair` prints a new code at any time, also while the workbench runs:

```bash
java -jar anvil-workbench.jar pair --data /srv/anvil-workbench
```

### As an image

The image runs the same JAR. Run it on the host's network, with the host's Docker socket, and with its data
directory mounted at the same path inside and outside, because the containers it starts use the host's Docker:

```bash
docker run -d --name anvil-workbench --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /srv/anvil-workbench:/srv/anvil-workbench \
  anvil-workbench
docker exec anvil-workbench workbench pair
```

The Dockerfile is `anvil-workbench/workbench-server/docker/Dockerfile`. Host networking needs a Linux host;
Docker Desktop on macOS and Windows keeps it inside its own virtual machine.

## Pair your machine with it

On your machine, with the code the workbench printed:

```bash
java -jar anvil-workbench.jar add workstation 192.168.1.20:7400 4F2A9C01D3
```

`workstation` is any name you choose. Pairing pins the workbench's certificate, as SSH pins a host's key, and
stores a token the workbench issued. The first workbench you pair becomes your default.

| Command | Does |
| --- | --- |
| `list` | shows your workbenches and the default, marked `*` |
| `default NAME` | runs scenarios on that workbench unless something names another |
| `default local` | runs them on this machine again |
| `remove NAME` | forgets a workbench |

Pairings live in `~/.anvil/workbenches/workbenches.json`. Point `ANVIL_WORKBENCHES`, or the `anvil.workbenches`
property, at another directory to share them between your machines, for example a folder Nextcloud syncs.
The file holds the tokens: whoever has it can run processes on your workbenches.

## Choose where scenarios run

A run uses, in this order:

1. the workbench `-Danvil.workbench=NAME` names, or `local`;
2. the workbench the `ANVIL_WORKBENCH` environment variable names;
3. your default from `default`;
4. this machine.

```bash
./gradlew anvilTest -Danvil.workbench=workstation
```

An engine that cannot reach its workbench fails at once with the reason; it never falls back to this machine on
its own. Both machines need the same Anvil version.

## Decide what the workbench allows

The workbench's operator, whoever runs it, writes `workbench.toml` in its data directory:

```toml
name = "workstation"
port = 7400

[policy]
execution = ["docker"]       # "docker", "local", or both
reaches = ["*.mojang.com"]   # hosts processes may contact; "*" is every host
processors = 8               # most a scenario may use; 0 is the machine's
memoryMegabytes = 16384
```

- `execution` lists how processes may run. With `docker` alone, the default, every process runs in a container,
  including the servers and proxies a scenario runs locally, so nothing a test starts touches the machine.
  `local` runs them directly on the machine.
- `reaches`, `processors` and `memoryMegabytes` are what the workbench offers scenarios; a scenario that
  [requires](../requirements/index.md) more is skipped there with the reason.

## Debug a process

Name the servers or proxies whose JVMs should accept a debugger, or `*` for all:

```bash
./gradlew anvilTest -Danvil.debug=proxy -Danvil.debug.suspend=true
```

Each gets a listener named `debug`, and the run prints where to attach, such as
`attach a remote JVM debugger to 127.0.0.1:23145`. Attach IntelliJ IDEA's Remote JVM Debug configuration to that
address. It works the same on a workbench on another machine, whose ports appear on your machine's loopback. With
`anvil.debug.suspend`, a process waits for the debugger before it starts, and its startup deadline grows to
30 minutes.

## How it works

- A process's addresses mean the same on both machines: the engine mirrors every loopback port a workbench's
  process uses on its own loopback, at the same port, and carries each connection there through the
  workbench's connection.
- Files of your machine, such as plugin JARs and the platform agent, are sent once by the SHA-256 of their
  content; a JAR that did not change is not sent again.
- Simulated players run on the workbench next to the processes they join.
- A session server on your machine's loopback, such as a test's Yggdrasil stand-in, is made reachable from the
  workbench's processes at the same port.
- When your machine disconnects, the workbench stops everything it started for it.

## Limits

- NeoForge runs only on this machine, and so does a process whose Java installation a declaration names by its
  path on this machine. A workbench on another machine skips such a scenario with the reason.
- Docker forwards ports through its own network, so a process does not see the address a player connected
  from. A test that joins players from different loopback addresses needs `local` execution.
- Execution providers you hand the engine yourself exist only in your JVM and serve only this machine.
- If something on your machine already listens on a port a workbench's process got, that port cannot be
  mirrored and the process fails to prepare; running it again picks another port.
