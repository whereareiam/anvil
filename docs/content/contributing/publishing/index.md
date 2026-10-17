---
title: Building and publication
description: Build verified consumer artifacts and use the maintainer-controlled publication workflows.
---

Use the Gradle wrapper from the repository root. Anvil compiles with Java 21; live tests provision
the runtimes required by their selected distributions.

```shell
./gradlew build
./gradlew dockerTest
./gradlew test -Panvil.testMode=full
```

The normal build runs architecture, unit, and focused integration checks, and needs no Docker. `dockerTest`
runs the tests that need a Docker engine. Full mode additionally starts real platforms. [Testing Anvil](../testing/index.md) explains focused tasks and filters.

## Local publication

Publish Anvil before building the standalone consumer example:

```shell
./gradlew publishToMavenLocal
./gradlew -p anvil-testkit/fixtures clean build
./gradlew -p examples/proof-of-patience build anvilTest
```

Use the same `-PanvilVersion=<version>` in all three commands when overriding the version. The
independent builds resolve Maven Local in their plugin and dependency repositories. To isolate
artifacts, also pass `-Dmaven.repo.local=/absolute/path/to/repository` to all three commands.

Both consumer builds stay outside root project discovery. The independent fixture invocation
checks public artifact contracts without the root composite's source substitution. The example
checks plugin resolution, dependency wiring, and its `src/anvil` journeys against the publication.
Fixture artifacts themselves remain internal and are not published. See
[fixture verification](../testing/fixtures/index.md#verify-published-contracts) for variant ownership.

## Align published modules

The main `bom` derives constraints from every published root project, including the project-model
API, Gradle model API, artifact binding, and Gradle producer. Platform imports align requested
libraries without adding those libraries to an application's classpath. They do not select versions
in Gradle's `plugins {}` block.

The root build coordinates publication for all normal projects and prepares the fixture repository
used by independent consumer tests. A module's own publication task publishes that module; use the
root commands for a complete consumer installation.
The fixture repository includes every configured publication, including BOMs, so standalone Gradle
tests verify imported constraints from published metadata without composite substitution.

## Package the IntelliJ plugin

```shell
./gradlew :anvil-integration:integration-intellij:buildPlugin
./gradlew :anvil-integration:integration-intellij:verifyPlugin
```

The ZIP is written to `anvil-integration/integration-intellij/build/distributions`, and
`verifyPlugin` checks it against the baseline and newest supported IDE builds. Development builds,
releases and full pull request verifications retain the ZIP as an `anvil-intellij-plugin-<version>` artifact for
[installation from disk](../../integrations/intellij/installation/index.md#install-a-development-build).

The Marketplace listing comes from the plugin module: `plugin.xml` holds the description, and
`marketplace/change-notes.html` holds the notes shown under **What's New**. Update the change notes
for every release before publishing it.

## Publish the IntelliJ plugin

A published GitHub release publishes the signed plugin to JetBrains Marketplace after its Maven
publication succeeds, so the listed plugin never precedes the artifacts it needs. Versions with a
pre-release suffix, such as `1.0.0-beta.1`, go to the `eap` channel; other versions go to the default
stable channel. Development builds never publish the plugin.

The release job reads these repository secrets:

| Secret | Value |
|---|---|
| `JETBRAINS_MARKETPLACE_TOKEN` | A Marketplace permanent token with upload permission for the plugin |
| `INTELLIJ_CERTIFICATE_CHAIN` | The PEM certificate chain used to sign the plugin |
| `INTELLIJ_PRIVATE_KEY` | The PEM private key matching that certificate |
| `INTELLIJ_PRIVATE_KEY_PASSWORD` | The private key's password |

JetBrains documents creating the [signing certificate](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)
and the [Marketplace token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html).
Marketplace accepts automated uploads only for an existing plugin: upload the first signed ZIP through
the Marketplace website, then let releases publish later versions. To sign and publish manually, export
the same four environment variables and run:

```shell
./gradlew :anvil-integration:integration-intellij:publishPlugin -PanvilVersion=<version>
```

## Verify a pull request

Two workflows start by themselves on a pull request:

| Workflow | Checks |
|---|---|
| **Pull request · Metadata** | The `Area: Title` title and the release label |
| **Pull request · Checks** | `./gradlew build -Panvil.intellij=false`: architecture, segment linkage, unit and runtime tests, and the live matrix data |
| **Pull request · Checks**, Docker engine tests | `./gradlew :anvil-environment:execution:execution-docker:dockerTest`, with the test image loaded from the Actions cache |
| **Pull request · Checks**, IntelliJ | The IntelliJ integration's build and tests, in a run of its own |

The checks start no server and do not verify the IntelliJ plugin distribution. The Docker engine tests run in
the image `anvil.docker.testImage` in `gradle.properties` names. The job loads it from the Actions cache keyed by
that reference and pulls it only on a miss, because shared runners exhaust registries' anonymous pull limits. The
**Docker image cache** workflow saves the entry on every push to `dev` and weekly, so it stays filled; pull
requests only read it. The IntelliJ integration
builds separately because the IntelliJ Platform Gradle Plugin's indexing of the IDE fails now and then on a
fresh machine (JetBrains/intellij-platform-gradle-plugin#2192); such a failure fails only the IntelliJ run,
which can be rerun alone. Every other CI build passes `-Panvil.intellij=false`, which leaves the IntelliJ
modules out of the build. A new push cancels the run of the
previous revision, and a draft pull request is not checked until it is ready for review.

Live verification is maintainer-requested, because it starts every verified platform:

1. Open GitHub **Actions** and select **Pull request · Full verification**.
2. Run the workflow from the default branch and supply the open pull request number.

```shell
gh workflow run pull-request-verification.yml -f pull-request=<number>
```

The workflow resolves the merge commit at dispatch time and verifies that immutable revision. It
repeats the build, verifies the IntelliJ plugin, builds the fixtures and the example against the
published artifacts, and runs direct-server and proxy compatibility, player capabilities,
session/extension behavior, and the example's journeys. Live groups use separate runners and reuse
the build job's published artifacts and task cache.

A run keeps as artifacts only what someone downloads: the IntelliJ plugin, and the JUnit results of a
job in which a test failed (`test-results`, or `test-results-live-<group>`). The test summary is on
the run page either way. The build job hands its Maven artifacts and task outputs to the later jobs
through the Actions cache, under a key of that run.

The run reports a **Full verification** status on the pull request's head commit, so it appears among
the pull request's checks and links to the run. Request a new run after the revision changes; the
status belongs to the revision it verified. Pull request workflows never publish artifacts and only
read the shared caches.

## Publish a development build

Run **Development build** manually and select the intended branch, normally `dev`. It verifies the
build, the Docker engine tests and the standalone consumers, and then publishes a branch-qualified version such as `dev-a123bcd`;
slashes in branch names become hyphens. Development versions do not use a `-SNAPSHOT` suffix. The IntelliJ
plugin is verified beside it and does not hold the publication back. Enable **live** to also run the live
suites before publication. Pushes do not trigger publication.

```shell
gh workflow run development.yml --ref dev -f live=true
```

## Build on a clean machine

The server suite applies the Anvil Gradle plugin at the build's own version, so a machine whose Maven
Local does not hold that version publishes it first. A bootstrap build leaves the suite out:

```shell
./gradlew publishToMavenLocal -Panvil.bootstrap
./gradlew build
```

Every workflow does the same with a Maven repository of its own run.

## Prepare and publish a release

Release Drafter updates the draft on `dev` pushes or manual dispatch. Each merged pull request appears
as its title, sorted by the title's area, under the category of its label. Label changes before merging:

| Label | Release category | Version bump |
|---|---|---|
| `feature` | What’s new | Minor |
| `change` | Enhancements | Patch |
| `bug` | Fixes | Patch |
| `dependencies` | Dependencies | Patch |
| `major` | Accompanies a category | Major |
| `skip-changelog` | Excluded from the changelog | No category bump |

The default bump is patch. The pull request template and "Release notes" in `AGENTS.md` list the areas
a title may use.

The subtitle, summary, highlights and upgrade notes come from `.github/release-notes.md`, while the
`version` in its front matter matches the version the draft resolves to. Change that file through pull
requests; text typed into the draft is replaced on the next `dev` push. Fix the changelog itself by
editing titles and labels of merged pull requests, then run **Release drafter** again.

A published GitHub release triggers **Release**. It stops while the release still holds the draft's
subtitle or summary placeholder; otherwise it runs full verification including the live suites, Maven publication, and then
[IntelliJ plugin publication](#publish-the-intellij-plugin). The artifact
version comes from the tag, with an optional leading `v` removed. Nothing else starts the workflow.

Publication runs once after successful verification against the exact verified commit. The
publication job obtains registry credentials through the configured OIDC publishing action. Keep
POM metadata, licenses, service descriptors, and distinct plain/shaded artifacts valid when changing
release packaging. Failed verification prevents publication.
