---
title: Scenario selection and context injection
description: Choose an environment for a test method or class and receive its running context.
---

Use `@AnvilTest(YourScenario.class)` alongside JUnit's `@Test`. The scenario class implements
`AnvilScenarioDefinition` and has an accessible no-argument constructor. A public class with no
explicit constructors meets that requirement.

## Select one environment for a class

Using `PaperScenario` from the [first test](../../../getting-started/first-test/index.mdx), add
`com/example/test/ServerReadyTest.java` to the sources executed by your JUnit runner. With the Anvil
Gradle plugin, that means `src/anvil/java/com/example/test/ServerReadyTest.java`:

```java
package com.example.test;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.integration.junit.AnvilTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@AnvilTest(PaperScenario.class)
class ServerReadyTest {
	@Test
	void serverIsReady(ScenarioContext anvil) {
		assertEquals(ProcessState.READY, anvil.processes().server("server").state());
	}
}
```

The extension resolves parameters whose exact type is `ScenarioContext`. Access players through
`anvil.players()` and processes through `anvil.processes()`; it does not inject individual player or
process parameters.

## Override the class selection

A method's `@AnvilTest` takes precedence over the class annotation. Use that when one journey needs a
different topology or pinned distribution. Each method still receives a newly started environment.
A class annotation does not turn the scenario into a shared fixture, even with JUnit's per-class
instance lifecycle.

Keep reusable assertion code in helper methods, then call those helpers from separate annotated
methods for different definition classes. The [consumer example](https://github.com/whereareiam/anvil/tree/dev/examples/proof-of-patience)
uses this pattern to exercise one journey on Paper `1.21.11` and `26.1.2`.

## Declare a parameterized environment

When tests differ only in a few settings of one environment, such as a version or a plugin mode,
declare those settings on the test instead of writing one definition class per combination. Create an
annotation whose members are the settings, and mark it with `@AnvilEnvironment`. Place it in
`src/anvil/java/com/example/test/Lobby.java`:

```java
package com.example.test;

import me.whereareiam.anvil.integration.junit.AnvilEnvironment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@AnvilEnvironment(LobbyScenarios.class)
public @interface Lobby {
	String version() default "1.21.11";

	boolean whitelist() default false;
}
```

The factory named by `@AnvilEnvironment` builds the scenario from one declaration. Place it in
`src/anvil/java/com/example/test/LobbyScenarios.java`; `Scenarios.lobby(...)` stands for your own
scenario construction:

```java
package com.example.test;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.integration.junit.AnvilScenarioFactory;
import org.jetbrains.annotations.NotNull;

public final class LobbyScenarios implements AnvilScenarioFactory<Lobby> {
	@Override
	public @NotNull AnvilScenario create(@NotNull Lobby lobby) {
		return Scenarios.lobby(lobby.version(), lobby.whitelist());
	}
}
```

Annotate a test class or method with `@Lobby(whitelist = true)` and request `ScenarioContext` as with
`@AnvilTest`. A method's declaration replaces its class's, and a method or class declares exactly one
environment. Give equal declarations the same scenario name and different declarations different
names, because the name keys the scenario's workspaces.

## Lifecycle boundaries

Anvil starts the scenario in the extension's before-each callback. Request the context in the test
method or a supported before/after-each lifecycle method; it is not available during constructor
injection or before-all setup. The extension closes it when the method's extension store closes.

Do not hold contexts, players, or process handles in static fields for later methods. If files must
survive executions, select [workspace persistence](../../../building-blocks/environments/workspaces/persistence/index.md) deliberately.
