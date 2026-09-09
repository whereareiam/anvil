---
title: Fixtures
description: Use shared setup and real JAR artifacts when the tested boundary requires them.
---

Default to test-specific setup beside the test. Share a fixture when several tests use it or when
compiling and loading a real JAR is part of the behavior being verified.

| Fixture under `anvil-testkit/fixtures` | Purpose |
|---|---|
| `test-process` | A small executable for process startup, output, and lifecycle assertions |
| `test-server-plugin` | Commands and native observations installed in real servers |
| `test-extension` | External backend, capabilities, and agent operations loaded from real JARs |

These projects belong to the independent `anvil-test-fixtures` consumer build. They use ordinary
`src/main/java` and `src/main/resources` and produce real JARs. They are internal test artifacts and
are not published as consumer libraries.

Build the fixture artifacts from the repository root:

```shell
./gradlew :anvil-test-fixtures:build
```

Test tasks build the fixture JARs they consume. Fixture dependencies use catalog aliases for public
artifact coordinates. The root composite substitutes the current source projects using the same
coordinate declarations. This path does not require publishing Anvil first.

## Supply prepared artifacts to tests

Declare the needed fixture in the test module's Gradle configuration. Shared build logic selects
the artifact, establishes its build dependency, and passes the resolved path as a tracked test
input. `FixtureArtifacts` in `anvil-testkit/support` gives Java tests access to those paths.

For example, a repository test module that verifies an external protocol provider uses:

```kotlin
plugins {
	id("unit")
	id("fixtures")
}

fixtures {
	extension()
}

dependencies {
	testImplementation(projects.anvilProtocol.protocolApi)
}
```

The `fixtures` convention adds host-side test support. Put this test in the module's `src/test/java`:

```java
import me.whereareiam.anvil.protocol.api.provider.ProtocolProviderRegistry;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import me.whereareiam.anvil.testkit.support.TestExtensionLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalProviderTest {
	@Test
	void discoversPackagedProvider() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), false)) {
			assertEquals(List.of("fixture"), ProtocolProviderRegistry.discover().ids().stream().toList());
		}
	}
}
```

Run the module's `test` task. The assertion checks discovery from the prepared JAR. Passing `false`
hides installed parent protocol descriptors for this scope, while keeping parent API classes shared.
Pass `true` when testing selection alongside installed backends.

Gradle produces three extension variants: normal, broken-provider, and observation-only. Select the
variant that exercises the intended discovery behavior. For example, a missing-capability test
loads the broken-provider JAR; an agent-observation test loads the observation-only JAR.

Use `TestExtensionLoader` to open a prepared extension JAR in a scoped classloader. It owns discovery
isolation and restores the original loader when closed. Keep JAR creation and service-descriptor
selection in the fixture build so a test failure is about the loaded artifact's behavior.

## Keep the tested boundary intact

The external-extension fixture compiles against public Anvil contracts. Its runtime tests verify
provider selection, missing-capability diagnostics, and offline authentication defaults. Its live
tests verify agent-backed process capabilities without players, agent-backed player observations
without a Session adapter, native operations, and cleanup.

Neutral player providers compile against `capability-api`. Protocol and agent adapters use
`capability-protocol-api` and `capability-agent-api`, respectively;
embedded handlers compile against `agent-server-api`. The fixture contains these roles to test the
packaged boundary, using `compileOnly` dependencies so parent-provided API classes are not bundled
into its JAR. Shared native operation descriptors use `agent-api`; host wiring maps their metadata
to capability request descriptors without depending on agent client contracts.

Its in-process backend is a contract fixture, not another production Minecraft client. Use a real
backend for assertions about native protocol compatibility.

Resolve fixture JARs through Gradle dependency configurations. Do not guess a sibling `build/libs` filename;
that bypasses build dependencies and can pick a stale or plain artifact when a shaded one is needed.
Keep Anvil and platform APIs provided by the parent loader in extension-JAR tests.

## Verify published contracts

After [publishing Anvil to Maven Local](../../publishing/index.md#local-publication), run the fixture
build independently with the same version:

```shell
./gradlew -p anvil-testkit/fixtures clean build -PanvilVersion=0.0.1
```

Replace `0.0.1` with the version you published. This invocation resolves the public artifacts without
source substitution, so it checks publication metadata and missing API dependencies. Keep this
check alongside the standalone example; successful composite tests do not prove the published
artifacts contain the same contracts.

## Choose where new assertions belong

Put a one-off mock or helper with its owning test. Add a command to the server fixture when multiple
journeys need the same native observation. Extend the external fixture when provider discovery or
class-loader behavior requires an independently compiled artifact. Put reusable host-side loading
and artifact-access helpers in `anvil-testkit/support`, keeping them out of installed fixture JARs.

Keep `examples/proof-of-patience` consumer-shaped. It demonstrates adding Anvil to a plugin build and
running journeys; it is not the owner of framework-specific system assertions. See
[Unit and runtime tests](../unit-runtime/index.md) and [Live tests](../live/index.md) for commands.
