---
title: Unit and runtime tests
description: Run the closest regression test before architecture and cross-module discovery checks.
---

Run a focused test in its owning module first. For example, after changing capability composition:

```shell
./gradlew :anvil-capability:capability-runtime:test
./gradlew verifyArchitecture
./gradlew :anvil-testing:testing-runtime:test
```

A focused integration test can use temporary files, sockets, isolated protocol workers, or Gradle
TestKit without starting Minecraft. Its location follows the owner of the behavior.

## Use the relevant module suite

| Owner | Task |
|---|---|
| Scenario orchestration | `:anvil-engine:test` |
| Agent transport and extension loading | `:anvil-agent:agent-common:test` |
| Capability discovery and composition | `:anvil-capability:capability-runtime:test` |
| MCProtocol backend and worker contracts | `:anvil-protocol:protocol-mcprotocol:test` |
| Gradle scenario tooling | `:anvil-tooling:gradle:scenarios:test` |
| Curated plugin composition | `:anvil-tooling:gradle:bundle:test` |

Use `--tests '*ClassName'` to select the regression class when appropriate. Tests should establish an
observable contract or failure outcome, rather than repeat the implementation's steps.

## Check cross-module discovery

`anvil-testing/testing-runtime` exercises provider selection and capability composition without
Minecraft. It consumes actual runtime dependencies and the separately built external-extension JAR.

```shell
./gradlew :anvil-testing:testing-runtime:test
./gradlew :anvil-testing:testing-runtime:test --tests '*ExternalProviderDiscoveryIntegrationTest'
```

Use these tests for automatic and explicit provider selection, ambiguity, missing dependencies,
external service discovery, and offline authentication defaults. They complement module-level tests
by testing the installed composition rather than manually assembled implementation objects.

## Finish with the build

```shell
./gradlew build
```

The normal build skips the whole real-server test task. For behavior involving Minecraft sessions,
packet bindings, forwarding, or platform agents, also run the relevant [live coverage](../live/index.md).
For dependency or publication changes, verify a separate consumer through
[local publication](../../publishing/index.md#local-publication).
