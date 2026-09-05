---
title: Running environments
description: Select platforms and runtimes, configure proxy routes, and run automated or manual environments.
---

# Running environments

Anvil prepares and supervises the local server/proxy processes declared by a scenario. Use these
guides to choose the runtime and diagnose how players reach it:

- [Platforms and versions](./platforms/index.md): provider units, supported combinations, Java, and configuration formats.
- [Proxies and forwarding](./proxies/index.md): backend topology and native identity forwarding.
- [Authentication](./authentication/index.md): offline players and optional provider-owned account workflows.
- [Manual environments](./manual/index.md): foreground sessions and explicit LAN access.
- [Troubleshooting](./troubleshooting/index.md): startup, discovery, artifacts, and journey failures.

Automated and manual workflows share the same [scenario declarations](../writing-tests/scenarios/index.md).
Changing how an environment runs does not require a second set of test APIs. Manual mode still
requires EULA acceptance and compatible client versions, and agent endpoints remain loopback-only.
