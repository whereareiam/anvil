---
title: JUnit lifecycle
description: Understand scenario startup, context injection, and cleanup around each test.
---

# JUnit lifecycle

For each `@AnvilTest` invocation, the extension instantiates the selected scenario definition,
reads engine options, starts the scenario, and stores the resulting context for parameter
injection. A parameter is supported when its declared type is `ScenarioContext`.

The scenario is already running when the test method starts. Servers are ready according to the
scenario startup timeout, but a player is disconnected until its `Session` capability is told to
connect. Use a bounded capability wait after each asynchronous action.

When the test returns or throws, the extension closes the context and then the engine. Closing
destroys registered players and releases process, execution, and workspace resources. Cleanup
continues across independent resources and preserves failures for diagnostics. See [workspace
cleanup](../workspaces/cleanup.md) for retention and cache behavior.

Do not retain a `ScenarioContext` or player beyond the test that received it. A player destroyed by
`player.destroy()` cannot be reused, while a name can be registered again after destruction.
