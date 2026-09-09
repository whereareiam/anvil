---
title: Overview
description: Choose module, cross-module, and live tests for the behavior you change.
---

Start with the nearest test that observes the changed behavior. Broaden verification to architecture,
composition, and real platforms where the change crosses those boundaries.

| Change | Verification path |
|---|---|
| Local algorithm, cache, file writer, or API behavior | Owning module's unit/focused integration tests |
| Dependency graph or runtime provider composition | Architecture checks and `anvil-testkit/tests/runtime` |
| Packet bindings, sessions, identity, routing, or native agents | Exact worker contracts and `anvil-testkit/tests/server` |
| Published plugin or artifact wiring | Local publication and the standalone consumer |
| Documentation only | Links, navigation, symbols, and Scriptorium compilation |

- [Unit and runtime tests](./unit-runtime/index.md) covers fast checks and module ownership.
- [Fixtures](./fixtures/index.md) explains how to prepare reusable test code and real test JARs.
- [Live tests](./live/index.md) covers real platforms, worker contracts, filters, and logs.

## Keep test locations predictable

Production modules use ordinary `src/test`, normally mirroring the production package. Cross-module
runtime and live tests live under `anvil-testkit/tests`. The independent fixture consumer build in
`anvil-testkit/fixtures` uses `src/main` for its executable, plugin, and extension artifacts. Reusable
host-side artifact access and classloader support belongs in `anvil-testkit/support`.
Consumer journeys use `src/anvil` in the standalone example.

Do not combine live and non-live methods in one test class behind tags. Keep assertions about Anvil
internals out of the consumer example. Real online-account tests do not run in CI.

For maintainer-requested pull request checks, see
[Building and publication](../publishing/index.md).
