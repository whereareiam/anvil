---
title: Engine extensions
description: Add global engine and scenario lifecycle contributions without importing scoped service APIs.
---

Use an `EngineExtension` for behavior that belongs to the engine or to every scenario it runs, such
as diagnostics, observation, or application-owned resources. Its contract is in
`me.whereareiam.anvil:api`. A capability, platform provider, or cache implementation uses its own
[scoped extension point](../index.md).

## Observe scenario outcomes

This complete extension reports when a ready scenario is attached and when it finishes. Put it in
`src/main/java/com/example/diagnostics/ScenarioLogExtension.java`. It depends on `api`, Lombok, and
JetBrains annotations; the application supplies its message sink.

```java
package com.example.diagnostics;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.engine.EngineExtension;
import me.whereareiam.anvil.api.engine.EngineRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Observes each ready scenario and its eventual lifecycle outcome.
 */
@RequiredArgsConstructor
public final class ScenarioLogExtension implements EngineExtension {
	private final @NotNull Consumer<String> messages;

	@Override
	public void install(@NotNull EngineRegistration registration) {
		registration.scenarios(scenario -> {
			String name = scenario.definition().getName();
			messages.accept("Started " + name);
			return successful -> messages.accept((successful ? "Completed " : "Failed ") + name);
		});
	}
}
```

The returned `ScenarioAttachment` belongs to one scenario. The engine installs it before the setup
hook and finalizes it before the underlying scenario resources. If a later attachment or setup hook
fails, already-installed attachments receive an unsuccessful outcome.

## Install the extension

Use the [embedding classpath](../../integrations/embedding/index.md), including the platform
providers and protocol libraries your scenario needs. This fragment assumes supplied `EngineOptions options`
and `AnvilScenario scenario`:

```java
import com.example.diagnostics.ScenarioLogExtension;
import me.whereareiam.anvil.launcher.AnvilLauncher;

try (var engine = AnvilLauncher.builder()
		.options(options)
		.extension(new ScenarioLogExtension(System.out::println))
		.build();
	 var context = engine.start(scenario)) {
	System.out.println("Ready processes: " + context.processes().all().size());
}
```

For a normal run, the sink receives `Started <name>` followed by `Completed <name>`. A failed setup
produces `Failed <name>`. `context.close()` means normal caller completion; an application that
catches a failed journey should call `context.finish(false)`. Prior lifecycle failures still
prevent successful finalization.

The builder can be consumed once. Extensions install during `build()`, before any scenario starts.
Use explicit `.extension(...)` registration for application contributions; no service file is needed
for this example.

## Assign resource ownership

`registration.own(resource)` transfers a shared `AutoCloseable` to the engine. Shared resources close
in reverse registration order after all scenarios, including when a later extension fails to install.
An attachment owns only resources acquired for its scenario. Release untransferred acquisitions
inside an `attach` call that fails before returning its attachment.

The logging example borrows its message sink and does not close `System.out`. If your extension
opens a file or subscribes to an external observer, assign that resource to the engine or return a
scenario attachment that closes it at the appropriate lifetime.

## Supply a custom scenario assembly

Application extensions add lifecycle behavior; they do not select the engine's scenario factory.
A custom assembly supplies its `ScenarioFactory` directly to the core engine builder constructor:

```java
import me.whereareiam.anvil.engine.AnvilEngineBuilder;

// factory and options are supplied by your assembly.
try (var engine = new AnvilEngineBuilder(factory).options(options).build()) {
    // Prepare or start scenarios through engine.
}
```

This requires the `engine` artifact alongside `api`. The factory is borrowed. If it depends on
shared resources that should close with the engine, transfer them through an extension using
`registration.own(resource)`; release any resources that have not been transferred if assembly fails.

`ScenarioFactory.create(scenario, observer)` acquires the complete topology and returns an owned `ScenarioContext`
before any process generation starts. Implement the context's `start()` to bring remaining
processes to readiness; the engine then attaches global extensions and runs setup once. Both
contracts are in `me.whereareiam.anvil.api.scenario`.

The launcher supplies the default scenario factory. Applications building a different assembly bind their
scoped services behind the same lifecycle boundary. The engine's full-start convenience prepares
a context and completes its startup; individual controls use that context's process group.

A failed acquisition releases resources before returning. A prepared owner must release all its
resources through `finish(successful)`, including inputs for processes that never started. Honor
the supplied observer before each generation launches and expose that managed handle through later
process lookup. Execution assigns an opaque `RunningProcess.executionId()` UUID; consumers use
that identity when separating replacement output. See [individual process controls](../../building-blocks/environments/actions/restarts/index.md#start-and-stop-individual-components)
for the consumer lifecycle.

Test registration failures, setup failures, repeated closure, attachment order, and the final outcome.
See [Lifecycle and ownership](../../contributing/architecture/lifecycle/index.md) for the framework's
ordering guarantees.
