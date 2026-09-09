---
title: Overview
description: Learn how Anvil's declarations become a running environment that your tests can control.
---

Anvil separates the environment you declare from the running objects your test uses. Start with
what should run, choose its inputs, then use the resulting context to control processes and players.

Consider a plugin that sends a welcome message when Alice joins. The scenario declares a server,
its platform and executable, and the plugin JAR to install. When the scenario starts, a context
gives the test a running server and a player manager. The test creates Alice, connects her, and
waits for the plugin's message through a capability.

## From declaration to observation

```text
Declare a scenario
  ├─ Platform: which server or proxy implementation?
  ├─ Distribution: which exact executable?
  └─ Workspace: which plugins, configuration, and data?
           ↓ start
Use the running context
  ├─ Processes: console, addresses, lifecycle, and process capabilities
  └─ Players → capabilities → actions and observations
           ↓ close
Release players and processes, then finalize workspaces
```

The examples in this section explain each part of that flow. You can read them before setting up
Anvil; [Installation](../installation/index.md) and [First test](../first-test/index.mdx) show how to
run the complete example. Later task guides cover the full configuration options.

## Follow the workflow

Read the first four concepts while thinking about the environment you want to declare. Then learn
how to access that environment once it is running, before adding player actions to the test.
The sidebar and each page's next step follow this same sequence.

| Step | Concept | The question it answers |
|---|---|---|
| 1 | [Scenarios](./scenarios/index.md) | What complete environment does my test need? |
| 2 | [Platforms](./platforms/index.md) | Which server or proxy implementation should each part use? |
| 3 | [Distributions](./distributions/index.md) | Which exact executable should run? |
| 4 | [Workspaces](./workspaces/index.md) | How do my plugin, configuration, and data reach it? |
| 5 | [Running context](./context/index.md) | How does my code access this active environment? |
| 6 | [Processes](./processes/index.md) | How do I control its running servers and proxies? |
| 7 | [Players](./players/index.md) | How do I create a client inside this run? |
| 8 | [Capabilities](./capabilities/index.md) | How do players and processes expose typed actions and observations? |

The context comes before process and player operations because it is the entry point to both.
Capabilities follow their two owners: players and processes.

You do not need to leave this sequence to follow the detailed setup or API guides linked along the
way. They are follow-up reading. After the last concept, continue to Installation and First test.

Start with [Scenarios](./scenarios/index.md), the declaration that brings the environment together.
