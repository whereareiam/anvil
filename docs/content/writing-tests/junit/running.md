---
title: Running JUnit journeys
description: Run managed journeys locally and select the right task for unit or live behavior tests.
---

# Running JUnit journeys

Managed journeys start real Minecraft processes and native protocol clients. Run them explicitly
with the Anvil task so a normal unit-test invocation does not unexpectedly launch a server:

```shell
./gradlew anvilTest
```

The task reads the project's Anvil configuration, including protocol provider, artifact mappings,
cache and workspace directories, Java selection, and EULA acknowledgement. Configure those values
before troubleshooting a scenario failure; the test code generally only chooses a scenario class.

Use [scenario catalogs](../scenarios/catalogs.md) when the project needs discovery, version
matrices, or manual groups. Use [manual environments](../../running-environments/manual/index.md)
for an interactive foreground run. Process console output and retained workspaces are covered by
[troubleshooting](../../running-environments/troubleshooting/index.md).
