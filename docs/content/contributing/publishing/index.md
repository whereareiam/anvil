---
title: Building and publication
description: Build verified consumer artifacts and use the maintainer-controlled publication workflows.
---

Use the Gradle wrapper from the repository root. Anvil compiles with Java 21; live tests provision
the runtimes required by their selected distributions.

```shell
./gradlew build
./gradlew test -Panvil.testMode=full
```

The normal build runs architecture, unit, and focused integration checks. Full mode additionally
starts real platforms. [Testing Anvil](../testing/index.md) explains focused tasks and filters.

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
./gradlew :anvil-integration:integration-intellij:intellij:buildPlugin
./gradlew :anvil-integration:integration-intellij:intellij:verifyPlugin
```

The ZIP is written to `anvil-integration/integration-intellij/intellij/build/distributions`, and
`verifyPlugin` checks it against the baseline and newest supported IDE builds. Successful verification
workflows retain the ZIP as an `anvil-intellij-<version>` artifact for
[installation from disk](../../integrations/intellij/installation/index.md#install-a-development-build).

The Marketplace listing comes from the plugin module: `plugin.xml` holds the description, and
`marketplace/change-notes.html` holds the notes shown under **What's New**. Update the change notes
for every release before publishing it.

## Publish the IntelliJ plugin

A published GitHub release publishes the signed plugin to JetBrains Marketplace after its Maven
publication succeeds, so the listed plugin never precedes the artifacts it needs. Versions with a
pre-release suffix, such as `1.0.0-beta.1`, go to the `eap` channel; other versions go to the default
stable channel. Development publication and branch-qualified builds never publish the plugin.

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
./gradlew :anvil-integration:integration-intellij:intellij:publishPlugin -PanvilVersion=<version>
```

## Request pull request verification

Pull request build and live verification is maintainer-requested:

1. Open GitHub **Actions** and select **Pull request verification**.
2. Run the workflow from the default branch and supply the open pull request number.
3. Review its resolved merge revision and the build, live, and consumer results.
4. Request a new run after changing the pull request revision.

The workflow resolves the merge commit at dispatch time and verifies that immutable revision. It
runs build/runtime checks, independent fixture compilation, direct-server and proxy compatibility,
player/session/extension behavior, and standalone consumer journeys. Live groups use separate runners and reuse the build job's
published artifacts and task cache.

A lightweight **Pull request metadata** workflow automatically validates the title and release labels.
The manual verification repeats that validation. Pull request verification does not publish Maven
artifacts.

## Publish a development build

Run **Development publication** manually and select the intended branch, normally `dev`. After
verification succeeds, it publishes a branch-qualified version such as `dev-a123bcd`; slashes in
branch names become hyphens. Development versions do not use a `-SNAPSHOT` suffix. Pushes do not
trigger publication.

## Prepare and publish a release

Release Drafter updates the draft on `dev` pushes or manual dispatch. Label changes before merging:

| Label | Release category | Version bump |
|---|---|---|
| `feature` | Features | Minor |
| `change` | Changes | Patch |
| `bug` | Fixes | Patch |
| `dependencies` | Dependencies | Patch |
| `major` | Accompanies a category | Major |
| `skip-changelog` | Excluded from the changelog | No category bump |

The default bump is patch. Complete the release summary, verified Java/Minecraft/platform coverage,
and any public API, DSL, or packaging migration notes before publishing the draft.

A published GitHub release triggers **Release** verification, Maven publication, and then
[IntelliJ plugin publication](#publish-the-intellij-plugin). The artifact
version comes from the tag, with an optional leading `v` removed. For a rehearsal, run **Release**
manually with the intended version and leave `publish` disabled. A manual run uses its selected ref;
it does not create a release or move a tag.

Publication runs once after successful verification against the exact verified commit. The
publication job obtains registry credentials through the configured OIDC publishing action. Keep
POM metadata, licenses, service descriptors, and distinct plain/shaded artifacts valid when changing
release packaging. Failed verification prevents publication.
