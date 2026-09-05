---
title: Contributing
description: Work on Anvil's implementation, tests, build artifacts, and documentation.
---

# Contributing

This section is for changes to Anvil itself. You do not need to modify this repository to add a
capability, protocol backend, or agent operation; those workflows belong under
[extending Anvil](../extending/index.md).

- [Architecture](./architecture/index.md): module ownership, dependency direction, and runtime boundaries.
- [Testing Anvil](./testing/index.md): module-owned checks, runtime integration, real-server tests, and fixtures.
- [Building and publication](./publishing/index.md): local artifacts and CI/release behavior.
- [Writing documentation](./documentation/index.md): Scriptorium content, navigation, and preview conventions.

Read the repository's `AGENTS.md` and the owning module before changing a public contract. Keep
examples aligned with current behavior, run the closest relevant checks, and include live tests
when process, protocol, forwarding, or observed player behavior changes.
