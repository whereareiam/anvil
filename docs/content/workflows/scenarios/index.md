---
title: Overview
description: Prepare reusable environments, launch them from a terminal, and join them with your Minecraft client.
---

Use the scenarios workflow when you want to open a prepared environment and exercise your plugin
by hand. Anvil starts the declared servers and proxies, installs their assets, and keeps a terminal
open while you join with your own Minecraft Java Edition client.

For example, keep a Paper environment ready for checking a command's output, or prepare a proxy and
lobby to inspect a login or routing flow. The environment declaration is the same `AnvilScenario`
used by [automated tests](../testing/index.md); this workflow changes how you launch and use it.

## Prepare, run, and join

1. [Catalogs and groups](./catalogs/index.md): give your prepared environments names and register them
   with the runner.
2. [Running scenarios](./running/index.md): list environments, start one, and switch or restart it.
3. [Joining an environment](./joining/index.md): find the entrypoint and connect from this machine or
   an explicitly enabled LAN listener.
4. [Runner controls](./controls/index.md): inspect status and logs, send console commands, and stop.

Complete [installation](../../getting-started/installation/index.md) first. The umbrella plugin supplies
foreground tooling; a smaller installation uses `me.whereareiam.anvil.scenarios` with explicit
platform, protocol, and capability choices. The catalog guide provides a Paper example that you can
launch without writing a JUnit test.

## Reuse the building blocks

Describe servers, proxies, plugin JARs, seed data, and persistence in the shared
[environment building blocks](../../building-blocks/environments/index.md). A setup hook can also
create [simulated players](../../building-blocks/players/index.md) before the environment is handed
to you. Install the capabilities that those setup actions require.

Anvil manages its processes and simulated clients. You control your rendered Minecraft client yourself;
the runner does not automate its interface. Use an automated test when the sequence and assertions
should execute unattended, and use the same prepared environment here when you need to inspect it
interactively.
