---
title: Tooling extensions
description: Expose custom capability actions and observations to IntelliJ, the foreground shell, and other tooling clients.
---

A tooling extension contributes named actions, input forms, and observations. Its Java handlers run
in the project's Anvil runtime. Clients receive portable descriptions and results; they do not load
your capability classes or need a custom IDE plugin.

Install your capability and its provider normally; see [capability providers](../capabilities/index.md).
This guide assumes your own `Counter` player capability has `long add(long amount)` and `long value()`
methods. The independent fixture under `anvil-testkit/fixtures/test-tooling-extension` contains a
complete capability, provider, and tooling extension compiled against public contracts.

## Compile the extension

In the extension library, use the same Anvil version as the application. Import Anvil's BOM and
compile against the host SPI. Your capability API is an ordinary dependency so its types remain
available when the extension is discovered, even on targets that do not expose the implementation.

```kotlin
dependencies {
    compileOnly(platform("me.whereareiam.anvil:bom:0.0.1"))
    compileOnly("me.whereareiam.anvil:tooling-extension-api")
    implementation("com.example:counter-api:1.0.0")
}
```

Replace the example capability coordinate with your own. Do not shade Anvil APIs into the extension.
`tooling-api` contains portable data contracts; `tooling-extension-api` adds host-side registration
and core target access. An extension does not depend on IntelliJ or runner implementation classes.

## Register a player action and observation

Create an action class at `src/main/java/com/example/tooling/IncrementCounterAction.java`.
Its constructor declares the inputs; its typed handler receives the player and current `Counter`:

```java
package com.example.tooling;

import com.example.counter.Counter;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionInput;
import me.whereareiam.anvil.tooling.api.type.action.ActionInputType;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.player.PlayerCapabilityAction;
import me.whereareiam.anvil.tooling.extension.api.model.ToolingArguments;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Increments an external player capability through its typed tooling action.
 */
public final class IncrementCounterAction extends PlayerCapabilityAction<Counter> {
	/**
	 * Declares the contribution without acquiring runtime resources.
	 */
	public IncrementCounterAction() {
		super(Counter.class, ActionDefinition.builder().id("example.counter.add").displayName("Increment counter")
				.inputs(List.of(ActionInput.builder().name("amount").displayName("Amount").type(ActionInputType.INTEGER).defaultValue("1").build())).build());
	}

	@Override
	public @NotNull ActionResult execute(
			@NotNull SimulatedPlayer player,
			@NotNull Counter counter,
			@NotNull ToolingArguments arguments
	) {
		return ActionResult.builder().columns(List.of("Player", "Value"))
				.rows(List.of(List.of(player.name(), Long.toString(counter.add(arguments.integer("amount")))))).build();
	}
}
```

Create `src/main/java/com/example/tooling/CounterValueObservation.java` for the read operation:

```java
package com.example.tooling;

import com.example.counter.Counter;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.player.PlayerCapabilityObservation;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the current value from the supplied external capability.
 */
public final class CounterValueObservation extends PlayerCapabilityObservation<Counter> {
	/**
	 * Declares the contribution without acquiring runtime resources.
	 */
	public CounterValueObservation() {
		super(Counter.class, ObservationDefinition.builder().id("example.counter.value").displayName("Counter").build());
	}

	@Override
	public @NotNull ObservationValue observe(
			@NotNull SimulatedPlayer player,
			@NotNull Counter counter
	) {
		return ObservationValue.builder().text(Long.toString(counter.value())).build();
	}
}
```

Register those contributions in `src/main/java/com/example/tooling/CounterTooling.java`:

```java
package com.example.tooling;

import me.whereareiam.anvil.tooling.extension.api.ToolingExtension;
import me.whereareiam.anvil.tooling.extension.api.ToolingRegistration;
import org.jetbrains.annotations.NotNull;

public final class CounterTooling implements ToolingExtension {
    @Override
    public void register(@NotNull ToolingRegistration registration) {
        registration.action(new IncrementCounterAction());
        registration.observation(new CounterValueObservation());
    }
}
```

Add this service descriptor:

```text
src/main/resources/META-INF/services/me.whereareiam.anvil.tooling.extension.api.ToolingExtension
```

Its content is the implementation class name:

```text
com.example.tooling.CounterTooling
```

Registration is synchronous and closes when `register` returns. Identifiers must be namespaced
and unique within their contribution kind. Labels and descriptions are optional; clients fall back
to identifiers. Capability-specific base classes expose contributions only on targets with the required capability.

## Choose a target and declare availability

| Base classes | Handler target | Example |
|---|---|---|
| `PlayerAction` / `PlayerObservation` | `SimulatedPlayer` | Inspect or change player-owned state |
| `ProcessAction` / `ProcessObservation` | `RunningProcess` | Inspect a proxy or server |
| `ScenarioAction` / `ScenarioObservation` | `ScenarioAccess` | Compare values across several servers or proxies |

The generic bases live in `tooling.extension.api.action` and `.observation`. Choose a scoped
specialization to contribute behavior:

| Scope | Action package | Observation package |
|---|---|---|
| Scenario | `action.scoped` | `observation.scoped` |
| Player | `action.scoped.player` | `observation.scoped.player` |
| Process | `action.scoped.process` | `observation.scoped.process` |

These package suffixes belong under `me.whereareiam.anvil.tooling.extension.api`.
Each object owns its portable definition and behavior. `ToolingRegistration` accepts contributions
through `action(...)` and `observation(...)`, and rejects subclasses of the generic bases that do not
extend a supported scoped specialization.

For required capabilities, extend `PlayerCapabilityAction<C>` or `ProcessCapabilityAction<C>`
and pass the capability class and definition to the parent constructor. The observation equivalents
are `PlayerCapabilityObservation<C>` and `ProcessCapabilityObservation<C>`. Capability variants live
beside their ordinary player or process counterparts. Their handlers receive
the typed capability alongside its owner. The capability is resolved for each call, including
availability checks; it is not cached when registration or scenario discovery occurs.

An ordinary action can override `availability(target)`. A capability action overrides
`availability(target, capability)`. Return an `ActionAvailability` with an explanation when the
operation is temporarily unavailable. For example, a replication action can require both proxies
to be ready. The runner resolves the target and checks availability again at invocation, so a
displayed action is not a promise that a later request will succeed. Requests carry the environment
identity; requests from a previous run are rejected. Inputs are validated before execution.

The runner owns scenario and target lifetimes. Do not retain or close borrowed targets or capabilities.
Acquire temporary handler resources with try-with-resources and keep durable state in scenario-owned
capabilities. Contribution constructors only declare metadata; they do not acquire runtime resources.

Keep observations short and read-only. Return an `ObservationValue` with `INFO`, `SUCCESS`, `WARNING`,
or `ERROR` tone. If one observation fails, its error remains visible while other values are retained.

## Describe inputs and results

Inputs support `TEXT`, signed 64-bit `INTEGER`, arbitrary-precision `DECIMAL`, `BOOLEAN`, and exact
`CHOICE` values. They are required by default. Set `required(false)` for an optional value, or supply
a valid `defaultValue`. Choice inputs declare their allowed `choices`. Unknown argument names and
invalid values are rejected before invocation.

Wire requests represent scalar argument values as strings. The interactive shell also accepts JSON
numbers and booleans and converts them to those representations.

Handlers read validated values through `ToolingArguments.text`, `optionalText`, `integer`, `decimal`,
and `booleanValue`. Sensitive text inputs use `sensitive(true)`; they cannot publish a default and
are excluded from input history and retained drafts. Clients render them as password fields.

An `ActionResult` can contain a message and a table. Every row must match the number of columns.
Use `successful(false)` when the operation completes with an unsuccessful domain result. Exceptions
report invocation failures. A message action may acknowledge submission without proving the later
in-game effect; use an observation or log assertion for that evidence.

## Update existing extensions

Extensions using the earlier unscoped action or observation packages must update their imports to the
scoped packages above and recompile their JARs. The Maven coordinate remains
`me.whereareiam.anvil:tooling-extension-api`, and the `ToolingExtension` service filename is unchanged.

## Install and use it

Add the extension to the consuming project's scenario runtime:

```kotlin
dependencies {
    add("anvilRuntimeOnly", "com.example:counter-tooling:1.0.0")
}
```

Use your actual coordinate, and ensure the capability implementation is installed too. Start a
scenario that creates a player exposing the capability. In IntelliJ, select that player under
**Players**, choose **Increment counter**, and select **Open action…**. Enter an amount and select
**Run**. The table shows the result and the Counter observation updates on subsequent snapshots.
Scenario and process contributions appear in **Environment**. The same API can expose an Identica
replication check through `ProcessCapabilityAction<Replication>` or a `ScenarioAction` comparing
several proxies.

In the foreground shell:

```text
actions
action example.counter.add player External {"amount":4}
```

The standard Anvil Gradle plugin includes `tooling-builtin` for chat, player commands, and
connection observations. That bundle is optional for independently assembled tooling runtimes;
`tooling-launcher` supplies executable entry points without installing that bundle. The reusable
`tooling-runner` has no dependency on the default launcher or built-in capability APIs. The fixture test verifies custom
capability composition and all target scopes with those built-in classes absent.
