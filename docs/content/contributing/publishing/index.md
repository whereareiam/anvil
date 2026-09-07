---
title: Building and publication
description: Verify Anvil locally, publish development artifacts, and prepare releases.
---

# Building and publication

## Local verification

```shell
./gradlew build
./gradlew test -Panvil.testMode=full
```

`build` checks architecture and runs unit and focused integration tests without Minecraft.
Full mode also downloads pinned distributions and starts real Minecraft servers and proxies. See [Testing Anvil](../testing/index.md)
for test groups, filters, and fixture ownership. Do not use real online accounts in CI.

## Local publication

Publish Anvil before building the standalone consumer example:

```shell
./gradlew publishToMavenLocal
./gradlew -p examples/proof-of-patience build anvilClasses
./gradlew -p examples/proof-of-patience anvilTest
```

Pass the same `-PanvilVersion=<version>` to each command when testing another version. Consumer
settings must include `mavenLocal()` in both plugin and dependency repositories. To isolate local
artifacts, pass `-Dmaven.repo.local=/absolute/path/to/repository` to each command.

The example is excluded from the root build and consumes published artifacts. Internal fixture
artifacts are not published.

## CI verification

Pull requests, development builds, and releases share `.github/workflows/verify.yml`:

- Build Anvil, run unit and integration tests without Minecraft, publish to an isolated Maven Local
  repository, and compile/test the consumer example and its scenarios.
- Run direct-server compatibility, Velocity compatibility, BungeeCord compatibility, player
  capabilities/sessions/extensions, and consumer journeys on separate runners.
- Reuse the build job's artifacts and Gradle task cache. Keep live tests sequential within each
  runner to avoid competing Minecraft processes.

Pull requests do not start this verification automatically. To verify one, open **Actions**, select
**Pull request verification**, choose the default branch, enter the open pull request number, and
run the workflow. The workflow resolves the pull request's merge revision and then runs the
build, unit and integration tests, live platform matrix, and standalone consumer journeys.

A lightweight **Pull request metadata** check runs automatically when a PR changes. It validates the
`Area: Title` format and exactly one release category label (`feature`, `change`, `bug`, or
`dependencies`), unless the PR carries `skip-changelog`; `major` may accompany a category label.
The manual verification workflow repeats this check before starting the build.

The workflow file runs from the default branch while the verification jobs check out the selected
pull request merge revision. Contents and pull request access remain read-only; checks write access
is used only for test reporting, and the workflow does not publish artifacts. Request a new run
after the pull request changes; a previous run remains tied to the revision it resolved when it started.
Manual workflow dispatch requires repository write access, and its checks should be reviewed from
the workflow run rather than treated as an automatic pull request gate.
If branch protection lists the former automatic pull request checks as required, remove or revise
those rules because an unrequested manual run cannot satisfy them.
Failed or cancelled verification prevents publication when the same verification workflow is used
by a release or development publication.

## Development publication

Run **Development publication** manually in GitHub Actions and select the `dev` branch.
It verifies the selected commit, then publishes `<branch>-<seven-character-sha>`, such as
`dev-a123bcd`. Slashes in branch names become hyphens. Versions do not use a `-SNAPSHOT` suffix.
Pushes do not trigger development publication.

## Release publication

Prepare the release tag and publish its GitHub release to trigger verification and Maven
publication. The artifact version comes from the tag, with an optional leading `v` removed.

To verify without publishing, run **Release** manually with the intended version and leave
`publish` disabled. A manual run uses the selected ref; it does not create a release or move a tag.

Publication runs once, after all verification succeeds, against the exact verified commit.
Only the publication job requests OIDC credentials through
`whereareiam/devops/actions/registry/maven-publish@v2`. Artifact Keeper must authorize the
repository's OIDC identity for the `packages` Maven repository; static Maven secrets are not needed.

## Release drafts

Release Drafter updates the draft on `dev` pushes or manual dispatch. Label PRs before merging:

| Label | Release notes | Version bump |
|---|---|---|
| `feature` | Features | Minor |
| `change` | Changes | Patch |
| `bug` | Fixes | Patch |
| `dependencies` | Dependencies | Patch |
| `major` | Add alongside a category label | Major |
| `skip-changelog` | Excluded | Not a version-bump label |

The default bump is patch. Draft names and tags use the version without a `v` prefix.
Before publishing, complete the summary, verified Java/Minecraft/platform coverage, and any
public API or configuration migration notes. Updating a draft does not publish artifacts.
