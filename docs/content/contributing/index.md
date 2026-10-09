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
the Bukkit platform agent, and the server-plugin fixture, applies the `in-server` convention instead:
its main classes compile for Java 11, the Bukkit agent and the fixture plugin against the Bukkit API of
Minecraft 1.16.5, and
`checkClassRelease` fails the build on a newer class. IntelliJ panel definitions use Kotlin in
`intellij-ui`; controllers, platform adapters, and engine code remain Java. See the
[UI ownership guidance](./architecture/modules/index.md) before adding a form. Managed distributions
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

A maintainer requests build and live verification through the manual workflow. See
[Building and publication](./publishing/index.md) for its revision and publication behavior.
