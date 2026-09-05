---
title: Building and publication
description: Build Anvil locally and understand ordinary, approved-live, and release CI workflows.
---

# Building and publication

Anvil separates ordinary verification from tests that start real Minecraft processes.

## Pull requests

The `verify` job builds Anvil, publishes to Maven Local, and builds the standalone consumer example.
After it succeeds, the
gated `live` job runs `./gradlew test -Panvil.testMode=full` against the native compatibility matrix.
Live jobs require the maintainer-approved `anvil-pr-live-tests` environment.

## Development and release

Development publication runs only through manual `workflow_dispatch`. Select the `dev` branch in
GitHub Actions when publishing a development build. It builds, runs the full test mode, compiles the
example scenarios, and publishes a commit-qualified snapshot. Pushes do not launch this workflow.
A published release first runs the same full verification, then publishes Maven modules and the
Gradle plugin using the release version.

## Release drafts and labels

Release Drafter refreshes the draft on pushes to `dev` or manual dispatch. This lightweight draft
update is separate from the manual development build/publication workflow. It uses these label
categories and version rules:

| Label | Release-note category | Version bump |
|---|---|---|
| `feature` | Features | Minor |
| `change` | Changes | Patch |
| `bug` | Fixes | Patch |
| `dependencies` | Dependencies | Patch |
| `major` | Add alongside a category label for breaking changes | Major |
| `skip-changelog` | Excluded from release notes | No category entry |

Unspecified version changes default to patch. `skip-changelog` controls inclusion in the notes; it
is not a version-bump label. Label pull requests before merging so the draft can group their entries.
The repository's other issue labels remain available but do not select a release-note category.

Draft names and tags use the resolved version without a `v` prefix. Before publishing, replace the
summary and compatibility placeholders with the verified Java, Minecraft, and platform coverage,
and explain relevant public API or configuration migrations. Updating a draft does not publish a
release or run the Maven publication workflow.

All workflows cache only immutable Anvil distributions, protocol runtimes, and JDKs. Credentials are
provided to the publication job by GitHub secrets and are not passed to tests or scenario processes.

## Local commands

```shell
./gradlew verifyArchitecture
./gradlew build
./gradlew :anvil-testing:testing-runtime:test
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full
./gradlew test -Panvil.testMode=full
```

The full mode downloads pinned server and protocol artifacts and starts local processes. Ordinary
`./gradlew test` runs the module-owned tests and `testing-runtime`, while the whole
`testing-server:test` task is skipped. The split is by Gradle module, not by tags on individual methods.

## Local publication

Use one version consistently across the plugin, public APIs, provider artifacts, and launcher.
For development against Maven Local:

```shell
./gradlew publishToMavenLocal
./gradlew -p examples/proof-of-patience build anvilClasses
```

Consumer settings need `mavenLocal()` in plugin and dependency repositories for this workflow.
This changes the local Maven repository; it does not publish to the remote registry. For an isolated
verification repository, pass `-Dmaven.repo.local=/absolute/path/to/a/temporary/repository` to both
publication and consumer invocations.

Examples are standalone builds, excluded from the root project graph: they consume the published
plugin rather than requiring it while configuring the build that produces it. To verify another
version, pass the same `-PanvilVersion=<version>` to both commands. Run the example's live journeys
with `./gradlew -p examples/proof-of-patience anvilTest` after local publication.

The launcher is a shaded assembly; the protocol and platform providers remain explicit dependencies.
Fixture artifacts under `anvil-testing/testing-fixtures` are internal tests and are not published.
The Gradle plugin marker publications and Maven coordinates are owned by the build conventions.

Do not run online-account authentication in CI. The authentication-task tests use a fake provider
and exercise configuration-cache reuse without authenticating a real account.
