---
title: Adding a Minecraft version
description: Add a protocol release, pin its runtime, add segments only where the code breaks, and cover the version with platform data and the live matrix.
---

A Minecraft version is supported when a protocol library release lists it, every segment that serves
the release links against it, each platform that runs it has Java data, and Anvil's live matrix runs
exactly the combinations the data calls verified. Work through the steps in order: each check shows
what the next step needs. Pinning and live tests need network access.

## Add or extend a release row

MCProtocolLib's releases are data in `anvil-player/player-library/library-mcprotocol/mcprotocol-releases.toml`.
One `[[release]]` row describes one MCProtocolLib build, which speaks one wire protocol:

| Field | Content |
|---|---|
| `version` | MCProtocolLib release identifier; for a snapshot build, its unique timestamped version, such as `1.21.11-20260512.221357-18` |
| `module` | The release's `group:name:version` coordinate, against which segments compile |
| `protocol` | Wire protocol number |
| `minecraft` | Every Minecraft version speaking that protocol; the highest is the release key |
| `java` | Minimum Java feature version of the release's runtime, at least 8 |
| `features` | `ONLINE_AUTHENTICATION` when its clients can log in to online-mode servers |

A row has no `verified` field. The build writes each release's verified versions into the copy it ships from the live
matrix, as [Cover the version in the live matrix](#cover-the-version-in-the-live-matrix) explains, and fails on a
hand-written one.

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
./gradlew :anvil-player:player-library:library-mcprotocol:pinLibraryReleases
```

For each release, the task resolves `module` at runtime scope and rewrites that release's
`[[release.artifact]]` tables in class path order: each JAR's module, the URL of the first library
repository that serves the same bytes, and its SHA-256. It keeps the hand-written fields and comments
and never changes an existing pin; a JAR whose checksum differs from its pin fails the task before
anything is written. Review the rewritten closure before committing it.

The workers download exactly this closure. A release with an empty pin is listed but cannot be
launched: preparation prints a warning, and creating a player that selects it is refused with the
pin task as the remedy.

## Add the block data

Simulated players read block data, such as collision shapes and friction, from `data-block`. The data is
read from each release's own vanilla client: its registries, the blocks' classes, tags and methods. A maintainer who
owns Minecraft runs the client headless with the standalone physics harness, which also writes the physics reference
traces, and commits what it writes as
`anvil-edition/edition-java/java-data/data-block/vanilla/<protocol>.blocks`. Nothing about blocks is
maintained by hand, and CI never downloads the client. Every Minecraft version of a protocol has the same block
registry, so the data is keyed by protocol number, and a new version of a protocol Anvil already speaks needs
nothing here.

For a new protocol, add the version's `[[release]]` row to `anvil-testkit/physics/harness/vanilla-clients.toml`,
naming the harness family whose client code fits it, and give it a `[[paper]]` row of `live-matrix.toml`, then pin
the client and read its data:

```toml
[[release]]
minecraft = "26.3"
harness = "v26"
```

```shell
./gradlew -p anvil-testkit/physics/harness pinVanillaClients
./gradlew -p anvil-testkit/physics/harness generateVanillaData -Pminecraft=26.3
```

`pinVanillaClients` pins Mojang's version metadata, client JAR and mappings by SHA-1. `generateVanillaData`
downloads them into `~/.anvil/vanilla-clients`, never into the repository, and writes the release's traces and its
`<protocol>.blocks`. Where the new client names a member the family's code uses differently, give the family's
releases their own sources in `src/<family>/release-<minecraft>/java`, compiled against that client alone; the
harness never bridges releases through reflection. It reaches a member the client keeps private only once that member
is widened: list it in the family's `src/<family>/accesstransformer.cfg`, or in the release's
`src/<family>/release-<minecraft>/accesstransformer.cfg` when only that release has it, one class or member per line
in NeoForge's access transformer format, such as
`public net.minecraft.client.Minecraft options:Lnet/minecraft/client/Options;` for a field or
`public net.minecraft.client.multiplayer.ClientChunkCache$Storage getIndex(II)I` for a method. The client
preparation widens exactly those into `client-widened.jar` in the cache, the same bytes from the same lists, and
fails when a list names something the client lacks. The harness fails rather than writing data that would not hold,
such as states numbered out of property order or a shape that depends on its position in a way the data cannot
record.

Each file holds, for every block in block ID order, its properties and states, its kind, collision kind and
behaviours where vanilla's movement code asks by class, identity or tag, its offset, friction, speed and jump
factors, bounce and stuck slowdown, and the movement tags it is in; for every state, its collision shape for a player
standing on it and for each other player case that differs, its fluid, whether it stops fluid flow and its sturdy
faces. Commit the file; the module's `generator` source set reads it strictly at build time.

The files must match the protocol library's releases: `checkBlockDataReleases`, part of `check`, fails unless the
protocol of each release of `mcprotocol-releases.toml` has a file, and every file a release. Then run the data's
tests, which check known facts, such as the state count and collision shapes, for every protocol:

```shell
./gradlew :anvil-edition:edition-java:java-data:data-block:test
```

Add the new protocol to the state counts `BlockRegistryTest` expects and to the protocols `JavaBlockDataTest` lists.

## Add the packet names

The frames a player receives are named from `data-packet`, which generates, from a pinned
[PrismarineJS minecraft-data](https://github.com/PrismarineJS/minecraft-data) release (MIT), the names of the packets a server sends in each connection state of each protocol: the packet mappings of
its `protocol.json` (the notice ships as `META-INF/LICENSE-minecraft-data-packets.txt`). Its
`anvil-edition/edition-java/java-data/data-packet/packet-data.toml` pins one minecraft-data release
and, per `[[release]]` row, the `protocol.json` of one protocol with its SHA-256, which Gradle resolves through the
`minecraft-data` repository the settings declare. For a new protocol, add a row and pin it:

```shell
./gradlew :anvil-edition:edition-java:java-data:data-packet:pinPacketData
```

`checkPacketDataReleases`, part of `check`, requires a row for the protocol of every release. A protocol
minecraft-data has no `protocol.json` for keeps a row without pins; its frames have no name,
and a tap refuses to select its packets by name. Add the new protocol to the protocols `JavaPacketDataTest` lists.

## Add the item data

Inventory items carry their type's registry key from `data-item`, which generates, from the same
minecraft-data release, each protocol's item registry by item ID: its `items.json` (the notice ships as
`META-INF/LICENSE-minecraft-data-items.txt`). Its
`anvil-edition/edition-java/java-data/data-item/item-data.toml` has the layout of `packet-data.toml`,
with one `items` file per row. For a new protocol, add a row and pin it:

```shell
./gradlew :anvil-edition:edition-java:java-data:data-item:pinItemData
```

`checkItemDataReleases`, part of `check`, requires a row for the protocol of every release. A protocol minecraft-data
has no `items.json` for keeps a row without pins; its items carry no type, and an inventory locator with a type
refuses to act. Add the new protocol to the protocols `JavaItemDataTest` lists.

Both minecraft-data modules apply the `module-minecraft-data` convention of `build-logic`'s `minecraftdata` group, which owns
reading minecraft-data, the pins files, the pin and check tasks and the generators' arguments for every kind of data
a module generates.

## Let linkage decide about segments

Release-specific code lives only in segments: projects named `V<major>_<minor>[_<patch>]` after the
release key they start at, inside a side folder such as the client's `mcprotocol-java`. Built-in capabilities
have no segments: they speak the Java Edition protocol, which the client segments translate.
Each segment compiles against its own release and serves every later release up to the next segment.
After adding a release, check every segment against the locked closures:

```shell
./gradlew checkSegmentLinkage
```

The `check` task runs the same verification. A segment's report is
`build/reports/segment/linkage.txt` in its project.

- **Everything links:** the new release reuses the newest segment of each side; no code changes.
- **The release is older than every segment of a side:** no segment serves it. Add one named after its key that
  starts a chain of its own, as `V1_16_5` does for the client and server packet names, or becomes the new start of
  the chain it shares packages with: move the chain's shared code into it and let the next segment extend it,
  replacing what changed, as `V1_21_1` extends `V1_20_6`.
- **A segment does not link:** the failure names the missing or changed class, field, or method. Add
  a segment named after the new release key in that side only, such as `V26_1_2` beside `V1_21_11`, with
  its classes in the package named after its folder in lower case, such as `.v26_1_2`. Let it extend the
  newest segment of the side and bring only the classes that differ or no longer link:

  ```kotlin
  segment {
      extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v12111)
      replaces("me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.InteractionPackets")
  }
  ```

  The segment carries every class of its base chain but the bases' adapters and the classes it names in
  `replaces`, which leave its JAR and its linkage manifest; the build fails when a replaced class is not a
  class of the base chain, or a class the segment still takes from its base uses it. Its adapter, a
  `McProtocolClientAdapter` extending the side's client base, lists the release's translations: the base's
  ones that still link and its own replacements. Release-neutral code shared by a chain, such as the client
  base and the value translations, lives in the earliest segment of the chain, and a translation whose bulk
  does not change between releases keeps that bulk in an abstract base there, so a later segment overrides
  only the part that changed. Linkage tells which classes to replace: a base class that no longer links
  against the new release fails `checkSegmentLinkage` until the segment replaces it. Only when the release
  changes the library's packages or sessions throughout does the segment start a new chain without a base,
  as `V1_18_2` does. Every segment depends on `mcprotocol-api`, and a segment whose release's components are
  Adventure's (MCProtocolLib for Minecraft 1.16 on) also on `mcprotocol-adventure`, whose `ComponentTranslator` the
  chain's `TextTranslator` extends with the Adventure accessors its releases share; a segment whose release's
  Adventure moved one of them, as Adventure 4.22 moved a click's value, brings its own `TextTranslator` and replaces
  its base's. An older release flattens its own text components in its segment. The side's `module-adapter` convention adds the new
  child automatically.
- **The protocol cannot express the release:** add or change the packet values in `protocol-api`
  and the area translations of every client segment, or change `McProtocolClient` in `mcprotocol-api`
  for the client itself. An external capability with segments of its own changes its port in its API's
  `packet` package and every segment implementing it.

Linkage does not cover what the game keeps in entity data. Each segment's `EntityDataTable` names, by entity
type, the index that carries each value the protocol models, and a release may move them while every class still
links: between 1.21.1 and 1.21.11 hanging entities gained their direction at index 8, which moved an item frame's
item from 8 to 9. Check
the table against the new release's entity data, and give the new release a segment of its own when an index moved.

A segment holds only port implementations and their `META-INF/services/<port>` descriptors; it never
wires a capability. Its folder must name a release key, and its classes must stay in its versioned
package, or the build fails.

## Update the worker contracts

`McProtocolWorkerContractTest` starts a real worker for every release on its locked closure and
checks the protocol number, the selected client segment, and the installed built-in capabilities. Add
a row for the new release key, its protocol number, and the client segment that serves it, then run:

```shell
./gradlew :anvil-player:player-library:library-mcprotocol:mcprotocol-common:test
```

`ClientSegmentLinkageTest` in the same suite checks the built client segments against every release
they serve, as the worker does. Run `./gradlew check` afterwards for the client segments' own tests and
the build conventions.

## Add platform Java data

Each platform provider owns its version data:

| Platform | Version data |
|---|---|
| Paper | `anvil-process/process-platform/platform-bukkit/paper-provider/src/main/resources/me/whereareiam/anvil/platform/paper/paper-versions.toml` |
| Spigot | `anvil-process/process-platform/platform-bukkit/spigot-provider/src/main/resources/me/whereareiam/anvil/platform/spigot/spigot-versions.toml` |
| NeoForge | `anvil-process/process-platform/platform-neoforge/neoforge-provider/src/main/resources/me/whereareiam/anvil/platform/neoforge/neoforge-versions.toml` |
| Velocity | `anvil-process/process-platform/platform-velocity/velocity-provider/src/main/resources/me/whereareiam/anvil/platform/velocity/velocity-versions.toml` |
| BungeeCord | `anvil-process/process-platform/platform-bungeecord/bungeecord-provider/src/main/resources/me/whereareiam/anvil/platform/bungeecord/bungeecord-versions.toml` |

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

`VERIFIED` means that Anvil's live matrix runs the combination. The matrix is the data file `live-matrix.toml`
at the repository root, the only place a combination is called verified. It has one table per platform, named by
the provider's id, and each row names a version and the Java the live suites run it on:

```toml
[[paper]]
minecraft = "1.21.11"
build = "132"
java = 21
```

- A release key runs directly on Paper: add a `[[paper]]` row with the version, its pinned Paper build and its Java,
  in version order. The build registers its `releaseTest-<minecraft>` task, which runs the release suite against it,
  and CI gives it a job of its own. A version the IDE should list as its own scenario also gets a
  `Paper<version>SystemScenario` definition, such as `Paper2612SystemScenario`, that returns
  `CompatibilityScenarioFactory.releaseScenario(...)`.
- A current version also runs on Spigot, and behind Velocity and BungeeCord beside its Paper server: add a
  `[[spigot]]` row with the version, the Spigot JAR's SHA-256 and its Java.
- A version NeoForge supports also runs directly on NeoForge: add a `[[neoforge]]` row with the version, its pinned
  NeoForge release and its Java. `NeoForgeServerSystemTest` runs it without the fixture plugin, which NeoForge cannot
  load.

That is all the verified data needs. The build appends each platform's rows to the version data its provider ships
as the `[verified]` table, and writes each MCProtocolLib release's `verified` versions, every Minecraft version of the
release a matrix server runs, into the shipped `mcprotocol-releases.toml`. A hand-written `[verified]` table or
`verified` field fails the build, and so does a matrix server no release speaks. The scenarios request each row's
Java, and `CompatibilityScenarioFactoryTest` plans them without starting a server against the data the build wrote;
every build runs it through the server suite's `matrixTest` task:

```shell
./gradlew :anvil-testkit:tests:server:matrixTest
```

Run the release suite against the new version, then the platform suite when the version runs on the other
platforms:

```shell
./gradlew :anvil-testkit:tests:server:releaseTest-1.21.11 -Panvil.testMode=full
./gradlew :anvil-testkit:tests:server:platformTest -Panvil.testMode=full
```

A capability the version adds or removes, such as dialogs in `1.21.6`, is gated in its release test class with
`TestedRelease.isAtLeast(...)`.

Replace `1.21.11` with the new version. Read [Live tests](../testing/live/index.md) for the other
suites and for retained diagnostics.

## Update the documentation

Update the release and platform tables and the live matrix on
[Versions and compatibility](../../building-blocks/environments/platforms/versions/index.md), the Java
guide's table, the version list in the README and the FAQ, and any capability guide whose behavior
differs on the new version, such as the [Messages](../../building-blocks/players/capabilities/messages/index.md)
examples. Validate the documentation as described in [Documentation](../documentation/index.md).
