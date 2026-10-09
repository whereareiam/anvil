---
title: Unit and runtime tests
description: Run the closest regression test before architecture and cross-module discovery checks.
---

Run a focused test in its owning module first. For example, after changing capability composition:

```shell
./gradlew :anvil-capability:test
./gradlew verifyArchitecture
./gradlew :anvil-testkit:tests:runtime:test
```

A focused integration test can use temporary files, sockets, isolated protocol workers, or Gradle
TestKit without starting Minecraft. Its location follows the owner of the behavior.

## Use the relevant module suite

| Owner                                                   | Task                                                          |
|---------------------------------------------------------|---------------------------------------------------------------|
| Global registration, extensions, and scenario lifecycle | `:anvil-engine:test`                                          |
| Default scoped-service assembly and ownership           | `:anvil-launcher:test`                                        |
| Cache entry access and publication                      | `:anvil-environment:cache:test`                               |
| Artifact acquisition                                    | `:anvil-environment:provisioning:provisioning-artifact:test`  |
| Java provisioning                                       | `:anvil-environment:provisioning:provisioning-java:test`      |
| Workspace policies and snapshots                        | `:anvil-environment:provisioning:provisioning-workspace:test` |
| Process lifecycle and restarts                          | `:anvil-environment:execution:execution-managed:test`         |
| Platform planning and forwarding                        | `:anvil-platform:platform-planning:test`                      |
| Player registration, library selection, and observations | `:anvil-protocol:test`                                       |
| Agent clients, sessions, and observations                | `:anvil-agent:agent-client:test`                              |
| Native agent dispatch and extension loading              | `:anvil-agent:agent-server:test`                              |
| Capability discovery and composition                    | `:anvil-capability:test`                                      |
| MCProtocolLib library, worker contracts and segments   | `:anvil-protocol:protocol-mcprotocol:mcprotocol-common:test` and `:anvil-protocol:protocol-mcprotocol:mcprotocol-client:<segment>:check` |
| Gradle scenario tooling                                 | `:anvil-integration:integration-gradle:gradle-plugin:test`                        |
| Curated plugin composition                              | `:anvil-integration:integration-gradle:gradle-capabilities:test`                   |
| Root build discovery and fixture publication                  | `./gradlew help`                                    |
| Foreground sessions and structured protocol              | `:anvil-tooling:tooling-runner:test`                          |
| Default tooling entry points and property parsing | `:anvil-tooling:tooling-launcher:test` |
| IntelliJ API contracts, sessions, discovery, persistence, and account operations | `:anvil-integration:integration-intellij:intellij-engine:test` |
| IntelliJ panels, dialogs, configuration editors, and console rendering | `:anvil-integration:integration-intellij:intellij-ui:test` |
| IntelliJ native Gradle import | `:anvil-integration:integration-intellij:intellij-gradle:test` |
| Installed plugin registration and composition | `:anvil-integration:integration-intellij:intellij:test` |
| Gradle tooling producer                                  | `:anvil-integration:integration-gradle:gradle-tooling:test`                |
| Build conventions: release data, segments, linkage, in-server release, and library layout | `:build-logic:check` and `:build-logic-settings:check`, which the root `check` runs |

Use `--tests '*ClassName'` to select the regression class when appropriate. Tests should establish an
observable contract or failure outcome, rather than repeat the implementation's steps.

Workspace tests mirror the `directory`, `preparation`, and `snapshot` implementation packages.
Agent client tests exercise host lifetimes and connections; server tests exercise embedded handlers
and endpoints. Cross-process assertions belong in the live suite, with the independently compiled
fixture checking the public client and server API dependencies.

## Check cross-module discovery

`anvil-testkit/tests/runtime` exercises protocol library selection, platform providers, and capability
composition without Minecraft. It consumes actual runtime dependencies and the separately built external-extension JAR.

```shell
./gradlew :anvil-testkit:tests:runtime:test
./gradlew :anvil-testkit:tests:runtime:test --tests '*ExternalProviderDiscoveryIntegrationTest'
```

Use these tests for ranked and explicit protocol library selection, ties between libraries, missing
dependencies, external service discovery, and offline authentication defaults. The runtime suite also
holds `JavaVersionTableDocumentationTest`, which compares the Java guide's table with the platform
providers' version data. They complement module-level tests
by testing the installed composition rather than manually assembled implementation objects.

## Check the IntelliJ package

Tests live with their implementation modules. API contract tests live in `intellij-engine`;
`intellij-api` has no test sources. Engine tests subscribe a recording listener to `SessionLog` and
run without the UI module. UI tests bind native presentation collaborators locally and run without
the packaging module. Mixed settings and account tests separate persistence from form behavior.
The controlled process fixture is shared through `intellij-engine/src/testFixtures`; it is never
packaged in the plugin. `intellij` retains only smoke tests for the production descriptor's settings,
run configuration, output factory, and optional Gradle registration.

Run all module tests together:

```shell
./gradlew :anvil-integration:integration-intellij:test
```


```shell
./gradlew :anvil-integration:integration-intellij:intellij:buildPlugin :anvil-integration:integration-intellij:intellij:verifyPlugin
```

The verifier checks the declared IDE baselines. To verify against an installed IDE, pass
`-Panvil.intellijVerificationPath=/absolute/path/to/idea`. IDE tests exercise native model import
and session behavior without starting Minecraft. [Project tooling](../../architecture/tooling/index.md)
describes the contract; [installation](../../../integrations/intellij/installation/index.md) describes
the ZIP workflow.

Validate UI changes against the current IDE as well as the compilation baseline:

```shell
./gradlew :anvil-integration:integration-intellij:intellij-ui:testCurrentIde --tests '*EnvironmentSectionTabsPlatformTest' -Panvil.intellijVerificationPath=/absolute/path/to/idea
```

This separate test task keeps baseline compilation intact while using the selected IDE's runtime,
test framework, and sandbox. Omit the path to use the configured current verification version.
The UI sandbox excludes optional Ultimate product hooks, whose obfuscated classes collide under
the test framework's flat class loader; Anvil, Java, Gradle, and the IDE's native themes remain loaded.
Section-switcher tests exercise both standard themes and the current IDE's Islands themes; editor
tab styling can differ from the Services-style `JBTabbedPane` used for Anvil's sections.

Both declared baselines currently verify as compatible with no deprecated or scheduled-for-removal
API usage. The remaining findings are experimental APIs the Gradle adapter and source discovery rely on
(`ExternalSystemBuildEvent` and `TrustedProjectsListener`); recheck them when raising the baseline.

UI tests lay out and paint their screens offscreen on every run. To inspect the renders, add
`-Panvil.uiCaptures`; the images are written to `intellij-ui/build/reports/ui-captures`:

```shell
./gradlew :anvil-integration:integration-intellij:intellij-ui:test -Panvil.uiCaptures
```

## Finish with the build

```shell
./gradlew build
```

The normal build skips the whole real-server test task. For behavior involving Minecraft sessions,
packet bindings, forwarding, or platform agents, also run the relevant [live coverage](../live/index.md).
For dependency or publication changes, verify a separate consumer through
[local publication](../../publishing/index.md#local-publication).
