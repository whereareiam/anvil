---
title: Overview
description: Run Anvil scenarios through JUnit Jupiter independently of the build tool.
---

Anvil's JUnit integration selects an environment through `@AnvilTest`, starts it before each test,
injects `ScenarioContext`, and closes the environment afterwards. Your test uses ordinary JUnit
Jupiter methods and assertions. This lifecycle belongs to the test framework and does not depend
on Gradle tasks or source-set conventions.

## Prepare the test runtime

Add `me.whereareiam.anvil:junit` at your chosen Anvil version to the dependencies of the tests that
use it. The published artifact brings the Anvil API, JUnit Jupiter, and launcher dependencies.
Supply the selected platform provider and matching agent, a protocol provider, and the capabilities
your tests use on the runtime classpath. Keep Anvil artifact versions aligned.

Use a JUnit Platform runner with the Jupiter engine. Configure Anvil's
[engine properties](../../building-blocks/environments/configuration/engine/index.md) on the JVM executing those tests, including EULA
acceptance, provider selection when needed, and any named artifact paths. The JUnit extension reads
those properties when it starts each scenario.

The [Gradle integration](../gradle/index.md) prepares these dependencies and settings for its
`anvilTest` task. Another JUnit launcher must supply the equivalent classpath and properties; it does
not need to invoke an Anvil Gradle task. The [embedding guide](../embedding/index.md)
explains the runtime components for applications assembling their own classpath.

## Select and use a scenario

Follow [Scenario selection and context injection](./selection/index.md) to annotate a test method or
class. The scenario and its [workspace assets](../../building-blocks/environments/workspaces/assets/index.md) declare
what starts; JUnit owns when it starts and closes.

With a running context, continue to [Players](../../building-blocks/players/index.md) and
[Assertions and waits](../../workflows/testing/assertions/index.md) to exercise the application.
Build-specific compilation, test selection commands, and reports belong in the build-tool guide;
for Gradle, see [Running tests](../gradle/testing/index.md).
