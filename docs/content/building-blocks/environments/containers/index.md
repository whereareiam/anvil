---
title: Containers
description: Run a database, a message broker or any other image as a process of the scenario, beside its servers and proxies.
---

A plugin that talks to Redis, a database or a broker needs that service running while it is tested. A
scenario declares it as a container process: a process like a server or a proxy, which runs an image
instead of a Minecraft platform. Anvil starts it before the scenario's servers and proxies, tells every process
where to reach it, and stops it with them. Any image works; Anvil knows nothing about what runs in it.

## Declare a container

In a scenario definition, import:

```java
import me.whereareiam.anvil.api.model.process.ContainerProcess;
```

Add the container to the scenario builder:

```java
.container(ContainerProcess.builder()
		.name("redis")
		.image("redis:7.2-alpine")
		.port(6379)
		.readyLog("Ready to accept connections")
		.executionProviderId("docker")
		.build())
```

- `name` is lowercase letters, digits and dashes, and differs from every other process and remote.
- `port` lists each port the container listens on, at least one; the first is its main port.
- `environment` and `command` set its environment variables and replace its image's command. Their values may
  name other processes, as below.
- `readyLog` is a regular expression the container prints once it is ready. Without one, it is ready once each
  of its ports reaches a listener.
- `executionProviderId` picks the execution provider that runs it, `null` for the scenario's. Local execution
  runs no images, so a scenario whose servers run on the host names `docker` here; its servers keep running on
  the host.

Docker execution needs docker-java on the test runtime; see [execution](../configuration/execution/index.md).

## Point the other processes at it

Write placeholders instead of addresses, in text assets and in JVM and program arguments of servers and
proxies, and in a container's environment and command:

| Placeholder | Becomes |
| --- | --- |
| `${anvil.process.redis.host}` | the host the reading process reaches the container at |
| `${anvil.process.redis.port}` | the port that reaches its main port |
| `${anvil.process.redis.port.<port>}` | the port that reaches one of its ports |

```java
.workspace(WorkspacePlan.builder()
		.asset(WorkspaceAsset.builder()
				.source(AssetSource.text("""
						redis:
						  host: "${anvil.process.redis.host}"
						  port: ${anvil.process.redis.port}
						"""))
				.target(Path.of("plugins/MyPlugin/config.yml"))
				.build())
		.build())
```

Each process gets the address it can reach. A process run by the same execution provider reaches the container
inside their environment, such as at its name in the scenario's Docker network. A process run by another
provider, such as a server on the host, reaches it at its published host port. Workspace snapshots are still told
apart by the text as declared, so an address that changes from run to run keeps the caches.

The same placeholders name servers and proxies, with `${anvil.process.<name>.port}` for their game port and
[listeners](../listeners/index.md) for further ports.

## Reach it from the test

```java
RunningContainer redis = anvil.processes().container("redis");
InetSocketAddress address = redis.port(6379);
RunningContainer.CommandResult keys = redis.execute("redis-cli", "--scan");
```

`port` is where the test reaches a container port, and `execute` runs a command inside the container. Like any
process, a container can be [frozen](../actions/freezes/index.md), so it keeps its connections but answers
nobody, as in a network split, [killed](../actions/kills/index.md), stopped and started, and restarted. A
restart starts a fresh container from the image.

Containers belong to the scenario that declares them. A process kept for the engine with
`ProcessLifetime.ENGINE` outlives them, so its files cannot name them.
