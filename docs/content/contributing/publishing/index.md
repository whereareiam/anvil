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
./gradlew -p examples/proof-of-patience build anvilTest
```

Use the same `-PanvilVersion=<version>` in both commands when overriding the version. The consumer
must resolve Maven Local in both plugin and dependency repositories. To isolate artifacts, also
pass `-Dmaven.repo.local=/absolute/path/to/repository` to both commands.

The example consumes published artifacts and is excluded from root project discovery. Fixture
artifacts are not published. A successful consumer run verifies plugin resolution, dependency
wiring, and the example journeys across that publication boundary.

## Request pull request verification

Pull request build and live verification is maintainer-requested:

1. Open GitHub **Actions** and select **Pull request verification**.
2. Run the workflow from the default branch and supply the open pull request number.
3. Review its resolved merge revision and the build, live, and consumer results.
4. Request a new run after changing the pull request revision.

The workflow resolves the merge commit at dispatch time and verifies that immutable revision. It
runs build/runtime checks, direct-server and proxy compatibility, player/session/extension behavior,
and standalone consumer journeys. Live groups use separate runners and reuse the build job's
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

A published GitHub release triggers **Release** verification and Maven publication. The artifact
version comes from the tag, with an optional leading `v` removed. For a rehearsal, run **Release**
manually with the intended version and leave `publish` disabled. A manual run uses its selected ref;
it does not create a release or move a tag.

Publication runs once after successful verification against the exact verified commit. The
publication job obtains registry credentials through the configured OIDC publishing action. Keep
POM metadata, licenses, service descriptors, and distinct plain/shaded artifacts valid when changing
release packaging. Failed verification prevents publication.
