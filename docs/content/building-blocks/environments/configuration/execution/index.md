---
title: Execution providers
description: Run host processes by default or configure a local Docker daemon through an embedded engine.
---

Local execution is the default and the normal Gradle workflow. Anvil also includes a Docker execution
provider for applications that construct the engine with explicit image settings.
The selected provider applies to the whole scenario.

## Choose an execution provider

| Choice | Requirements | Behavior |
|---|---|---|
| `local` | Compatible local or provisioned Java | Starts JVMs on the host with allocated listener ports |
| `docker` | Local Docker daemon, accessible workspace paths, configured Java images | Starts containers on a scenario network and publishes host endpoints |

Set `.execution("local")` or `.execution("docker")` on an `AnvilScenario` to override the engine's
`executionId`. Selecting Docker alone is insufficient: the service-discovered provider has no image
mappings, and the Gradle DSL does not currently expose image configuration.

## Configure the embedded Docker path

Use the [embedding dependencies](../../../../integrations/embedding/index.md), then add the Docker client dependencies
to your application build. The launcher contains the execution implementation; its embedded Docker
module does not bring these client libraries into the assembly:

```kotlin
dependencies {
	runtimeOnly("com.github.docker-java:docker-java:3.7.1")
	runtimeOnly("com.github.docker-java:docker-java-transport-zerodep:3.7.1")
}
```

`3.7.1` is the Docker client version in the current Anvil dependency catalog. Keep these dependencies
aligned with the Anvil release you embed.

Place this helper at `src/main/java/DockerScenario.java` in your application. Pass an existing scenario whose
servers use Java 21, plus a verified immutable Java 21 image reference from your own image selection:

```java
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.environment.execution.docker.execution.DockerExecutionProvider;
import me.whereareiam.anvil.environment.execution.docker.execution.DockerExecutionSettings;
import me.whereareiam.anvil.launcher.AnvilLauncher;

public final class DockerScenario {
	public static void run(AnvilScenario scenario, String java21Image) {
		var docker = new DockerExecutionProvider(DockerExecutionSettings.builder()
				.image("temurin:21", java21Image)
				.build());
		var options = EngineOptions.builder()
				.eulaAccepted(true)
				.protocolId("mcprotocol")
				.executionId("docker")
				.javaRequirement(JavaRequirement.builder()
						.featureVersion(21)
						.distribution("temurin")
						.build())
				.build();

		try (ScenarioEngine engine = AnvilLauncher.create(options, docker);
		     ScenarioContext context = engine.start(scenario.toBuilder().execution("docker").build())) {
			System.out.println(context.processes().get(scenario.getEntrypoint()).address());
		}
	}
}
```

Call the helper from the application's entry point. Supply an image such as
`your-registry/your-jdk@sha256:<verified-image-digest>`, replacing the entire placeholder with an actual
reference. The mapped image must contain a matching `java` executable and support the mounted scenario
workspace. Provide another mapping, such as `temurin:25`, for every other effective Java requirement.
Process or scenario Java overrides still take precedence over the helper's engine default.

Success means the processes and agents became ready and an entrypoint address was returned.
This helper closes the environment immediately afterward; perform your actions inside the context
lifetime to use it. Docker scenarios need the same explicit platform, agent, protocol, and capability
artifacts as local scenarios.

## Understand the current limits

The Docker daemon must be local and able to mount the host workspace. Unix sockets and Windows named
pipes are accepted; remote TCP `DOCKER_HOST` endpoints are rejected. Host Java sources are rejected.
Images are inspected for Java compatibility and their resolved identities are retained in the cache.
Use immutable digest references when repeatability across fresh caches matters.

For a proxy scenario, setting `backendNetworkExposure` to `PRIVATE` suppresses published backend game
ports. Combining it with `networkServerAccess(PROXY_ONLY)` creates an internal scenario Docker network.
These are `NetworkPolicy` builder settings under `me.whereareiam.anvil.api.model`; their enums live in
`me.whereareiam.anvil.api.type.network`. Local execution rejects that strict combination.

All scenario containers still share a network; this is not a separate access-control rule between
each backend and each proxy. Loopback host binding restricts host exposure, and does not isolate
processes from other programs on the same machine. Docker agents bind inside their containers and
are published to host loopback with per-run authentication.

Keep normal automated scenarios on loopback. A non-loopback game listener requires the explicit
[manual LAN settings](../../../../workflows/scenarios/joining/index.md#join-from-another-machine).
