---
title: Writing tests
description: Describe an environment, create players, and assert behavior through Anvil's public APIs.
---

# Writing tests

Anvil tests combine a scenario with a player journey. The scenario declares the processes and assets;
the test uses capabilities to perform actions and wait for observable results.

If this is your first scenario, start with [the installation and first-test guide](../getting-started/index.md).
For an existing Anvil project:

1. [Define a scenario](./scenarios/index.md) with pinned distributions and a connection entrypoint.
2. [Install workspace assets](./workspaces/index.md), including the packaged plugin under test.
3. [Create and manage players](./players/index.md) in your JUnit journey.
4. [Use capabilities](./capabilities/index.md) to drive actions and assert observations.

Put consumer scenarios and journeys in `src/anvil`, and keep ordinary plugin unit tests in `src/test`.
Run live journeys explicitly with `./gradlew anvilTest`.

Platform selection, proxy topology, account authentication, and foreground sessions are covered in
[running environments](../running-environments/index.md). Tests for Anvil's own implementation are
covered separately under [contributing](../contributing/testing/index.md).

## Waiting for console commands

Capture a process cursor before sending a setup or administration command:

```java
var proxy = anvil.proxy("proxy");
long cursor = proxy.logCursor();
proxy.sendCommand("fixture create Alice");
proxy.awaitLog("Created Alice", cursor, Duration.ofSeconds(10));
```

`awaitLog` matches only lines captured after that cursor. It waits on captured-output notifications,
not sleeps in test code. Cursors belong to one running-process instance. A timed-out wait includes
recent console output; closed output and evicted history fail explicitly instead of matching stale lines.
