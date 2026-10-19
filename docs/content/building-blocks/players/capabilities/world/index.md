---
title: World
description: Read the level and the blocks a player's client knows, wait for a block, and follow the block updates the server sends.
---

`World` is the world as the player's Minecraft client knows it: the level it is in, the chunk columns
the server sent it, and the block at each position of those columns, kept up to date by the block
updates that followed. Use it to check what a player sees of the world: the block a command placed, a
door a plugin opened, or a floor that must not change. Install the world unit and its Session
dependency, or explicitly select the default capability unit.

Every MCProtocolLib release Anvil ships has the capability. Block names and properties come from the
block data Anvil ships for the player's release; Minecraft 26.2 and 26.3 have none yet, so there every
block is known by its state ID only, as [described below](#releases-without-block-data).

## Read a block

This fragment belongs in a live test after Alice has connected. It uses `SimulatedPlayer alice`, the
test's `ScenarioContext anvil`, and these imports:

```java
import me.whereareiam.anvil.capability.world.BlockQuery;
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.capability.world.WorldWatch;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import java.time.Duration;
import java.util.Optional;
```

`block(x, y, z)` returns the block Alice's client knows at that position now, without waiting:

```java
World world = alice.capability(World.class);
Optional<SeenBlock> floor = world.block(0, 63, 0);
```

The result is empty while the client does not have the block's chunk column, or when the height lies
above or below the level: the client knows nothing there, and `World` does not guess. Right after a
join, the columns around Alice may still be on their way, so wait for the block instead:

```java
SeenBlock stone = world.awaitBlock(0, 63, 0, BlockQuery.named("stone"), Duration.ofSeconds(10));
```

`awaitBlock(x, y, z, condition, timeout)` reads the block on every check, so it also sees a block that
arrives with its column, and it returns at once for a block that already satisfies the condition.

## Follow block updates

Start a watch before the action, then wait for the update:

```java
WorldWatch blocks = world.watch();
anvil.processes().server("server").console().sendCommand("setblock 10 64 10 minecraft:gold_block");
SeenBlock gold = blocks.changed(10, 64, 10, BlockQuery.named("gold_block"), Duration.ofSeconds(5));
```

A watch follows the block updates the server sends after it was created: for one block, as a
`setblock`, a placed or broken block or an opened door causes them, and for many blocks of one chunk
section, as `fill` causes them. `changed(x, y, z, condition, timeout)` returns the block as the next
update of that position that satisfies the condition set it, passing over the updates that do not, and
the following wait continues after it. An update says only what the block became, not what it was.

A chunk column that arrives is not an update, so a block that only arrives with its column does not
change for a watch; use `awaitBlock` for it. Updates of a column the client does not have are ignored,
as the client ignores them.

`unchanged(x, y, z, duration)` fails as soon as the position has received an update since the
previous wait and returns normally once the duration has passed without one; it does not move the
watch. `expectChanged(x, y, z, condition)` describes the `changed` wait without waiting, for
[`player.awaitFirst(...)`](../../../../workflows/testing/assertions/index.md#wait-for-whichever-outcome-comes-first).

## Describe the block

A condition is any `Predicate<SeenBlock>`. `BlockQuery` builds the common ones and names them in the
failure of a wait, for example `Player did not see the block at 10 64 10 change to named
minecraft:gold_block`:

| Query | Matches a block |
| --- | --- |
| `named(name)` | of a block, such as `stone` or `minecraft:oak_door` |
| `withProperty(name, value)` | whose state has the property with that value, such as `open` and `true` |
| `air()` | that is `air`, `cave_air` or `void_air` |
| `state(stateId)` | in the state with that global ID |
| `any()` | that the client knows |
| `matching(description, condition)` | that satisfies any condition, under your description |

Combine them with `and`, `or` and `negate`:

```java
BlockQuery openDoor = BlockQuery.named("oak_door").and(BlockQuery.withProperty("open", "true"));
```

`SeenBlock` holds what the client knows about the block: `getX()`, `getY()` and `getZ()`;
`getStateId()`, the global ID of its state in Alice's release; and its identity: `name()`, such as
`minecraft:oak_door`, `property(name)`, `properties()` and `isAir()`. `describe()` writes the block as
vanilla writes a block state, such as `minecraft:oak_door[half=lower,open=true] at 4 64 2`.

## The level and its chunks

`dimension()` returns the level Alice is in, with its key, such as `minecraft:overworld`, its lowest
height and its height range; it is empty before her first login, while she configures and after her
connection ended. `loadedChunks()` returns the chunk columns her client has, and
`isChunkLoaded(chunkX, chunkZ)` tells whether it has one; a chunk coordinate is the block coordinate
divided by 16 and rounded down.

The world follows Alice's connection as her client does: a respawn into another level, a return to the
configuration phase and a disconnect forget every column. Each update a watch returns in `unread()`
carries the number of the connection that received it and the key of the level it changed.

## Releases without block data

Block state IDs are the same in every client of one release, so every release knows them, but their
names come from block data that Anvil ships per release. For a release without block data,
`world.knowsBlockNames()` is `false`, `identity()` of every block is empty and `isNamed()` is `false`.
`name()`, `property(...)`, `properties()`, `isAir()`, and the queries `named`, `withProperty` and
`air` then throw `UnknownBlockNameException` instead of answering, so a wait fails at once with the
reason rather than at its timeout. Compare such blocks by their state IDs:

```java
SeenBlock before = world.awaitBlock(10, 64, 10, BlockQuery.any());
anvil.processes().server("server").console().sendCommand("setblock 10 64 10 minecraft:gold_block");
SeenBlock after = blocks.changed(10, 64, 10, BlockQuery.state(before.getStateId()).negate());
```

The [Minecraft versions guide](../../../../contributing/minecraft-versions/index.md) tells where the
block data comes from and which releases have it.

## Inspect failures

`world.block(x, y, z)` returns what Alice knows now, without waiting, and `watch.unread()` returns
every update since the watch's previous wait. When a wait for a block fails, check whether the client
has the block's column, whether the block is in the level Alice is in, and whether the server sent an
update at all: a block that a plugin changes without telling the client stays as the client last knew
it.
