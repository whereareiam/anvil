---
title: Overview
description: Add Anvil to a Gradle build and run your first test against a real server.
---

Anvil runs the Minecraft environment that your test needs: server and proxy processes, installed
plugins, and native protocol players. Your test remains ordinary Java using JUnit Jupiter.

Follow these three stages:

1. [Concepts](./concepts/index.md) — explore each part of the environment through focused explanations, examples, and use cases.
2. [Installation](./installation/index.md) — configure Gradle and install the components for the example.
3. [First test](./first-test/index.mdx) — start Paper, connect a player, and assert a received message.

After the first test passes, use [Building blocks](../building-blocks/index.md) to prepare your own
environment and players. Then choose a [workflow](../workflows/index.md): automated Testing or a
prepared Scenario you join with a real client.

You need a Java 21 or newer build environment, Gradle, network access for the first downloads, and
enough memory to run the selected processes. Anvil resolves the managed processes' Java requirements
separately from the JVM running your build.

Already have a working environment? Choose [Testing](../workflows/testing/index.md) to automate a
journey, or [Scenarios](../workflows/scenarios/index.md) to make it available for human testing.
