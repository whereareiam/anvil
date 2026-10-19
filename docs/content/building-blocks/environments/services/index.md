---
title: Services
description: Run a database, a message broker or any other container beside a scenario's servers and proxies, and make it fail on purpose.
---

A plugin that talks to Redis, a database or a broker needs that service running while it is tested. A
scenario declares such services next to its processes. Anvil starts each service before the scenario's
processes, hands every process the address it reaches the service at, and removes the service after the
processes have stopped. Any container image works; Anvil knows nothing about what runs in it.

## Let Anvil reach Docker

Services run on the machine's Docker engine. Anvil carries the Docker service provider; add docker-java to
the test runtime, as for Docker execution:

```kotlin
dependencies {
	testRuntimeOnly("com.github.docker-java:docker-java-core:3.7.1")
	testRuntimeOnly("com.github.docker-java:docker-java-transport-zerodep:3.7.1")
}
```

Use `docker-java-core`, not the `docker-java` aggregate, whose Netty transport would stop the MCProtocolLib
player workers.

## Declare a service

In a scenario definition, import:

```java
import me.whereareiam.anvil.api.model.service.ServiceContainer;
```

Add the service to the scenario builder:

```java
.service(ServiceContainer.builder()
		.name("redis")
		.image("redis:7.2-alpine")
		.port(6379)
		.readyLog("Ready to accept connections")
		.build())
```

- `name` is lowercase letters, digits and dashes, and differs from every server, proxy and remote of the
  scenario.
- `port` lists each port the service listens on inside its container.
- `environment` and `command` set the container's environment variables and replace its image's command.
- `readyLog` is a regular expression the service prints once it is ready. Without one, the service is ready
  once each of its ports reaches a listener inside the container.

## Point processes at a service

Processes reach a service at another address than the test does, and which one depends on the execution
provider: under local execution they reach its published port on the host, under Docker execution its name
inside the scenario's network. Write placeholders instead of addresses, in text assets and in JVM and
program arguments:

| Placeholder | Becomes |
| --- | --- |
| `${anvil.service.<name>.host}` | the host the process reaches the service at |
| `${anvil.service.<name>.port}` | the port of a service with one port |
| `${anvil.service.<name>.port.<container port>}` | the port that reaches one of the service's container ports |

```java
.workspace(WorkspacePlan.builder()
		.asset(WorkspaceAsset.builder()
				.source(AssetSource.text("""
						redis:
						  host: "${anvil.service.redis.host}"
						  port: ${anvil.service.redis.port}
						"""))
				.target(Path.of("plugins/MyPlugin/config.yml"))
				.build())
		.build())
```

Workspace snapshots are still told apart by the text as declared, so an address that changes from run to
run keeps the caches. Files a setup hook writes itself are not filled in; read the address from
`anvil.services()` there.

Services belong to the scenario that declares them. A process kept for the engine with
`ProcessLifetime.ENGINE` outlives them, so its files cannot name them.

## Reach a service from the test

`anvil.services()` returns the scenario's running services:

```java
RunningService redis = anvil.services().get("redis");
InetSocketAddress address = redis.address(6379);
ServiceCommandResult keys = redis.execute("redis-cli", "--scan");
```

`address` is where the test reaches a container port. `execute` runs a command inside the container and
returns its exit code and output.

## Make a service fail

A test checks how its plugin copes with a service going away:

| Call | Effect |
| --- | --- |
| `pause()` | The container is frozen: it keeps what it holds, but connections to it hang, as in a network split. |
| `resume()` | A paused container carries on. |
| `kill()` | The container stops at once, as in a crash. |
| `restart()` | The container starts again at the same addresses and waits until it is ready. |

```java
redis.pause();
alice.chat("Hello");
bob.neverSees("Hello");
redis.resume();
```

`state()` tells whether the service is `STARTING`, `RUNNING`, `PAUSED` or `STOPPED`.
