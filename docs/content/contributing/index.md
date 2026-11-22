---
title: Overview
description: Change Anvil itself while preserving reproducible behavior and public contracts.
---

Use this section when changing Anvil's implementation, tests, build, or documentation. To add
behavior from your own library, start with [Extending Anvil](../extending/index.md).

| Task | Guide |
|---|---|
| Find the owner of a runtime behavior | [Architecture](./architecture/index.md) |
| Add a regression test and run the relevant suites | [Testing Anvil](./testing/index.md) |
| Support another Minecraft version | [Adding a Minecraft version](./minecraft-versions/index.md) |
| Write or preview a documentation page | [Documentation](./documentation/index.md) |
| Build consumer artifacts or prepare a release | [Building and publication](./publishing/index.md) |

## Start a focused change

Read `AGENTS.md`, inspect the working tree, and find the narrowest module that owns the behavior.
Read its build file, adjacent implementation, and tests before editing. Preserve existing staged and
unstaged work.

Anvil source targets Java 21. Code that Minecraft servers load, such as `agent-api`, `agent-server`,
the Bukkit platform agent, and the server-plugin fixture, applies the `module-java-legacy` convention instead:
its main classes compile for Java 11, the Bukkit agent and the fixture plugin against the Bukkit API of
Minecraft 1.16.5, and
`checkClassRelease` fails the build on a newer class. IntelliJ panels and dialogs use Kotlin in the
plugin's feature modules; controllers, services, and platform adapters remain Java. See the
[IntelliJ plugin modules](./architecture/modules/index.md#intellij-plugin-modules) before adding a screen. Managed distributions
may require another Java runtime; that requirement is separate from the framework's compilation
target.

When changing a public contract, update its callers, provider descriptors, packaging, tests, and
consumer documentation together. Keep a complete runnable example for public DSL changes. Choose
verification by the changed behavior, including live checks when protocol or platform behavior is
affected.

## Prepare the pull request

Explain the concrete behavior change and the checks you ran. Use an `Area: Title` pull request title
and one release category label: `feature`, `change`, `bug`, or `dependencies`. `major` can accompany
a category; `skip-changelog` excludes a change from release notes.

The title's description is past tense and says what changed for people who test with Anvil or extend
it: `Introduced`, `Changed`, `Fixed` or `Removed`, such as `Players: Fixed reconnects after a proxy
transfer`. The release notes sort by the area, so spell it exactly as one of these: `Engine`,
`Environments`, `Workspaces`, `Execution`, `Processes`, `Platforms`, `Players`, `Capabilities`,
`Protocol`, `Yggdrasil`, `JUnit`, `Gradle`, `IntelliJ`, `Launcher`. A change to one platform only may
use its name: `Paper`, `Spigot`, `NeoForge`, `Velocity`, `BungeeCord`. A `skip-changelog` pull request
may also use `CI`, `Build`, `Docs` or `Tests`.

Quick checks run on every pull request; a maintainer requests live verification through the manual
workflow. See [Building and publication](./publishing/index.md#verify-a-pull-request) for both.
