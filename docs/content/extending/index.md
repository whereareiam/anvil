---
title: Extending Anvil
description: Add capabilities, protocol backends, and platform-agent operations from your own project.
---

# Extending Anvil

Anvil separates public contracts, implementations, and runtime assembly. Choose the extension
boundary that owns the behavior rather than adding a concrete platform/library branch to the engine.

- [Capabilities](./capabilities/index.md): portable feature APIs and backend/agent adapters.
- [Protocol providers](./protocol-providers/index.md): another client library and optional authentication.
- [Agent operations](./agent-operations/index.md): typed behavior executed inside a real platform.

External implementations can be developed in their own repositories. Our MCProtocol-specific
adapters are examples of implementations, not dependencies that every external capability must use.
The public `Server` observation API and `AgentDirectory` are independent of the client packet library.

For using installed capabilities in tests, see [writing tests](../writing-tests/index.md). For changes
to Anvil's own implementation and build, see [contributing](../contributing/index.md).
