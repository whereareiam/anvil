---
title: Overview
description: Add capabilities, client backends, platform providers, and operations from your own library.
---

Choose the extension point that owns the behavior you need. You can build these extensions in a
separate repository and install them through normal dependency and workspace configuration.

## Add custom behavior first

The Echo example is the shortest complete extension path:

1. [Define a capability contract](./capabilities/contracts/index.md) for test authors.
2. [Define the agent operation and handler](./agent-operations/contracts/index.md) that perform the work.
3. [Connect the host adapter](./capabilities/agent-adapters/index.md) and
   [install the handler](./agent-operations/installation/index.md) as a workspace asset.
4. [Package and test](./packaging/index.md) the resulting library from a separate consumer.

Operation contracts must exist before the agent-backed adapter can call them. Handler installation
uses the same [workspace asset workflow](../building-blocks/environments/workspaces/assets/index.md) as other JARs.
For packet behavior, choose a [protocol adapter](./capabilities/protocol-adapters/index.md) instead.

## Add another runtime implementation

These are independent advanced paths, not prerequisites for writing a capability:

- [Protocol providers](./protocol-providers/index.md) integrate another native client library.
- [Platform providers](./platform-providers/index.md) provision another server or proxy distribution.

Both use the same [packaging and discovery checks](./packaging/index.md) before consumer installation.

Start from a working [first test](../getting-started/first-test/index.mdx). Use the same Anvil version
across your extension's API dependencies and the consumer build. Public player capabilities keep
platform SDKs, packet libraries, and transport types out of the test-facing contract.

If the existing APIs already express the behavior, implement that API for your backend. Define a new
capability when scenario authors need a new operation. Repository implementation changes belong in
[Contributing](../contributing/index.md).
