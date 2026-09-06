---
title: Running environments
description: Configure a test machine, choose a topology, inspect processes, and diagnose failed runs.
---

# Running environments

A scenario describes the servers and proxies your test needs. These guides explain how to run that
scenario on your machine and control it during a test. If you need to create your first scenario,
start with [Getting started](../getting-started/index.md).

## Prepare the environment

- [Configure an environment](./configuration/index.md): choose storage, Java installations, and timeouts; configure Gradle or embed the engine.
- [Platforms and versions](./platforms/index.md): select provider units and compatible Minecraft/client versions.
- [Proxies and forwarding](./proxies/index.md): connect a proxy to backend servers and configure identity forwarding.
- [Authentication](./authentication/index.md): choose offline players or provider-owned online authentication.

## Run and inspect it

- [Inspect and restart processes](./processes/index.md): find current process handles and verify reconnection after replacing a proxy or server.
- [Manual environments](./manual/index.md): open a foreground scenario and join it with your own client.
- [Troubleshooting](./troubleshooting/index.md): identify the failed stage, inspect retained logs, and reproduce one journey.

Automated tests and manual sessions use the same [scenario declarations](../writing-tests/scenarios/index.md).
Workspace assets and caches are described in [Writing tests](../writing-tests/workspaces/index.md).
