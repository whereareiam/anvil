# Contributing to Anvil

Thanks for wanting to make Anvil better. This page gets you from a fresh clone to a pull request; the
[contributing guides](https://anvil.whereareiam.me/docs/dev/contributing) go deeper.

## Before you start

- Read the [architecture guide](https://anvil.whereareiam.me/docs/dev/contributing/architecture): which module owns what, how modules
  may depend on each other, and the conventions the build checks for you.
- For anything larger than a fix, open an issue or a draft pull request first, so the shape can be agreed
  before the work.

## Build it

You need Java 21. On a clean machine, publish the bootstrap build once, then build everything:

```shell
./gradlew publishToMavenLocal -Panvil.bootstrap
./gradlew build
```

`build` runs unit tests and every architecture and convention check; it starts no Minecraft server and
needs no Docker. Pass `-Panvil.intellij=false` to skip the IntelliJ plugin's modules.

## Test it

Run the nearest module's tests first, then the checks that guard the structure:

```shell
./gradlew verifyArchitecture checkSegmentLinkage
```

Changes to protocol, platform or player behaviour also need the live suites, which start real servers:

```shell
./gradlew :anvil-testkit:tests:server:releaseTest-1.21.11 -Panvil.testMode=full
```

[Testing Anvil](https://anvil.whereareiam.me/docs/dev/contributing/testing) explains every suite, and
[Adding a Minecraft version](https://anvil.whereareiam.me/docs/dev/contributing/minecraft-versions)
walks through supporting a new release.

## Open a pull request

- Work in a `feature/...` branch and open the pull request against `dev`.
- Title it `Area: Description` with a past-tense description of what changed for users, such as
  `Players: Fixed reconnects after a proxy transfer`. The [contributing overview](https://anvil.whereareiam.me/docs/dev/contributing/) lists
  the areas and labels.
- Update the tests and the documentation under `docs/content` together with the code they describe.
- Describe what changed and which checks you ran.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
