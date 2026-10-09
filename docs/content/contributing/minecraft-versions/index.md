---
title: Adding a Minecraft version
description: Add a protocol release, pin its runtime, add segments only where the code breaks, and cover the version with platform data and the live matrix.
---

A Minecraft version is supported when a protocol library release lists it, every segment that serves
the release links against it, each platform that runs it has Java data, and Anvil's live matrix runs
exactly the combinations the data calls verified. Work through the steps in order: each check shows
what the next step needs. Pinning and live tests need network access.

## Add or extend a release row

MCProtocolLib's releases are data in `anvil-protocol/protocol-mcprotocol/mcprotocol-releases.toml`.
One `[[release]]` row describes one MCProtocolLib build, which speaks one wire protocol:

| Field | Content |
|---|---|
| `version` | MCProtocolLib release identifier; for a snapshot build, its unique timestamped version, such as `1.21.11-20260512.221357-18` |
| `module` | The release's `group:name:version` coordinate, against which segments compile |
| `protocol` | Wire protocol number |
| `minecraft` | Every Minecraft version speaking that protocol; the highest is the release key |
| `verified` | The versions the live matrix runs; nothing else |
| `java` | Minimum Java feature version of the release's runtime, at least 8 |
| `features` | `ONLINE_AUTHENTICATION` when its clients can log in to online-mode servers |

When the new Minecraft version speaks the protocol of an existing release and that build already
supports it, add the version to the release's `minecraft` list instead of adding a row. Each Minecraft
version belongs to exactly one release. Adding a version above a release's key changes the key; a
segment named after the old key must then be renamed, folder and package, because every segment
names a release key.

For a new protocol, add a row with one `[[release.artifact]]` table for the release module, its JAR
URL, and `sha256 = ""`. The file keeps the latest patch of each Minecraft major. When the build is
served by a repository that `settings.gradle.kts` does not list yet, add it to both
`libraryRepositories { url(...) }` and `dependencyResolutionManagement`.

## Pin the runtime closure

```shell
./gradlew :anvil-protocol:protocol-mcprotocol:pinLibraryReleases
```

For each release, the task resolves `module` at runtime scope and rewrites that release's
`[[release.artifact]]` tables in class path order: each JAR's module, the URL of the first library
repository that serves the same bytes, and its SHA-256. It keeps the hand-written fields and comments
and never changes an existing pin; a JAR whose checksum differs from its pin fails the task before
anything is written. Review the rewritten closure before committing it.

The workers download exactly this closure. A release with an empty pin is listed but cannot be
launched: preparation prints a warning, and creating a player that selects it is refused with the
pin task as the remedy.

## Let linkage decide about segments

Release-specific code lives only in segments: projects named `V<major>_<minor>[_<patch>]` after the
release key they start at, inside a side folder such as `mcprotocol-client` or `movement-mcprotocol`.
Each segment compiles against its own release and serves every later release up to the next segment.
After adding a release, check every segment against the locked closures:

```shell
./gradlew checkSegmentLinkage
```

The `check` task runs the same verification. A segment's report is
`build/reports/segment/linkage.txt` in its project.

- **Everything links:** the new release reuses the newest segment of each side; no code changes. The
  `26.1.2` release, for example, runs the `V1_21_11` client segment.
- **A segment does not link:** the failure names the missing or changed class, field, or method. Add
  a segment named after the new release key in that side only, such as `V1_21_11` beside `V1_18_2`.
  Copy the previous segment, change its package suffix to the new folder name in lower case, such as
  `.v1_21_11`, and adapt the code the failure names. The side's `module-adapter` convention adds the new
  child automatically.
- **The port cannot express the release:** change the port in the feature API's `packet` package,
  such as `MovementPackets`, and every segment implementing it, or `McProtocolClient` in
  `mcprotocol-api` for the client.

A segment holds only port implementations and their `META-INF/services/<port>` descriptors; it never
wires a capability. Its folder must name a release key, and its classes must stay in its versioned
package, or the build fails.

## Update the worker contracts

`McProtocolWorkerContractTest` starts a real worker for every release on its locked closure and
checks the protocol number, the selected client segment, and the installed built-in capabilities. Add
a row for the new release key, its protocol number, and the client segment that serves it, then run:

```shell
./gradlew :anvil-protocol:protocol-mcprotocol:mcprotocol-common:test
```

`ClientSegmentLinkageTest` in the same suite checks the built client segments against every release
they serve, as the worker does. Run `./gradlew check` afterwards for the capability segments' own
tests and the build conventions.

## Add platform Java data

Each platform provider owns its version data:

| Platform | Version data |
|---|---|
| Paper | `anvil-platform/platform-paper/platform-paper-provider/src/main/resources/me/whereareiam/anvil/platform/paper/paper-versions.toml` |
| Spigot | `anvil-platform/platform-spigot/platform-spigot-provider/src/main/resources/me/whereareiam/anvil/platform/spigot/spigot-versions.toml` |
| Velocity | `anvil-platform/platform-velocity/platform-velocity-provider/src/main/resources/me/whereareiam/anvil/platform/velocity/velocity-versions.toml` |
| BungeeCord | `anvil-platform/platform-bungeecord/platform-bungeecord-provider/src/main/resources/me/whereareiam/anvil/platform/bungeecord/bungeecord-versions.toml` |

Add the version to `known`. Add a `[[java]]` row only when the platform's Java requirement changes at
that version: `since`, `minimum`, an optional `maximum`, the `preferred` LTS release, and, for a
platform that can run above its maximum, `maximumBypassProperty`. A row applies until the next one
starts. Processes run only on LTS releases (11, 17, 21, 25, then every fourth), so `preferred` must be
one of them; the `[agent] minimumJava` raises the effective minimum and default.

Then update the table in the [Java guide](../../building-blocks/environments/provisioning/java/index.md#java-per-platform-version).
`JavaVersionTableDocumentationTest` compares it with the providers' data and prints the expected rows
when they differ:

```shell
./gradlew :anvil-testkit:tests:runtime:test --tests '*JavaVersionTableDocumentationTest'
```

## Cover the version in the live matrix

`VERIFIED` means that Anvil's live matrix runs the combination. The matrix is
`anvil-testkit/tests/server/src/anvil/java/me/whereareiam/anvil/testkit/tests/server/scenario/CompatibilityScenarioFactory.java`:

- A release key runs directly on Paper: add a `PaperBuild` with the version and its pinned Paper build
  to `DIRECT_PAPER`. A version the IDE should list as its own scenario instead gets a
  `Paper<version>SystemScenario` definition, such as `Paper2612SystemScenario`, registered in
  `paperReleaseScenarios()`. Either way `PlayerCapabilitiesSystemTest` runs the full player journey on it.
- A current version also runs on Spigot and behind Velocity and BungeeCord: register it with
  `registerVersion(...)`, which takes the Paper build and the Spigot JAR's SHA-256 and adds only the Spigot
  and proxy scenarios.

Then list exactly the matrix's combinations as verified: the release's `verified` versions in
`mcprotocol-releases.toml`, and each platform's `[verified]` table with the Java versions the matrix
plans. `CompatibilityScenarioFactoryTest` plans the matrix without starting a server and fails when
the data and the matrix differ; every build runs it through the server suite's `matrixTest` task:

```shell
./gradlew :anvil-testkit:tests:server:matrixTest
```

Run the live scenarios for the new version, then the player behavior suites when the version runs
them:

```shell
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*-1\.21\.11'
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*PlayerCapabilitiesSystemTest' -PanvilMatrixFilter='paper-1\.21\.11'
```

Replace `1\.21\.11` with the new version. Read [Live tests](../testing/live/index.md) for the other
suites and for retained diagnostics.

## Update the documentation

Update the release and platform tables and the live matrix on
[Versions and compatibility](../../building-blocks/environments/platforms/versions/index.md), the Java
guide's table, the version list in the README and the FAQ, and any capability guide whose behavior
differs on the new version, such as the [Messages](../../building-blocks/players/capabilities/messages/index.md)
examples. Validate the documentation as described in [Documentation](../documentation/index.md).
