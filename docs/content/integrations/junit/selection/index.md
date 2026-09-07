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
import me.whereareiam.anvil.junit.AnvilTest;
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
uses this pattern to exercise one journey on both catalog versions.

## Lifecycle boundaries

Anvil starts the scenario in the extension's before-each callback. Request the context in the test
method or a supported before/after-each lifecycle method; it is not available during constructor
injection or before-all setup. The extension closes it when the method's extension store closes.

Do not hold contexts, players, or process handles in static fields for later methods. If files must
survive executions, select [workspace persistence](../../../building-blocks/environments/workspaces/persistence/index.md) deliberately.
