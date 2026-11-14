---
title: World
description: Read the dimension the player is in, its type, environment and generation, and the server's worlds, and wait until the player enters another dimension.
---

`World` reports the dimension the player's client is in: the key of the level the server put it in, with
its namespace, such as `minecraft:overworld`, `minecraft:the_nether` or, for a world a plugin created
under the name `arena`, `minecraft:arena`. A client learns its dimension when it joins a level and each
time the server respawns it, which is also how a server moves a player to another dimension; the client
never picks one itself. Install the world unit and its Session dependency, or explicitly select the
default capability unit. Every MCProtocolLib release Anvil ships has it.

To move a player to another world, use the server's [Worlds](../../../environments/actions/worlds/index.md)
capability; `World` shows what the client was told.

## Wait until the player enters a dimension

This fragment belongs in a live test after Alice has connected. It uses `SimulatedPlayer alice`,
`Messages messages` and these imports:

```java
import me.whereareiam.anvil.capability.world.DimensionWatch;
import me.whereareiam.anvil.capability.world.World;
import java.time.Duration;
import java.util.Optional;
```

Start the watch before the action, then wait:

```java
World world = alice.capability(World.class);
DimensionWatch dimensions = world.watch();
messages.command("warp arena");
dimensions.entered("minecraft:arena", Duration.ofSeconds(10));
assertEquals(Optional.of("minecraft:arena"), world.dimension());
```

`entered(dimension)` matches the player joining a level of that dimension, or respawning in it from
another one. A respawn in the dimension the player was already in, such as after dying, does not enter
it. A key without a namespace, such as `the_nether`, is in the `minecraft` namespace, as in the game's
commands.

Each change is a `DimensionChange` with the dimension the player came `from` and the one it went `to`.
`from` is `null` when the connection had not joined a level yet, and equals `to` after a respawn in the
same dimension. `changed(matcher, timeout)` waits for any change a predicate accepts, for example a
proxy moving the player to another server's overworld:
`dimensions.changed(change -> change.getTo().equals("minecraft:overworld"), timeout)`.

## Read what the client knows about its level

`world.dimension()` is the dimension the player is in now, or empty while it is not connected or has not
joined a level yet. `world.level()` gives the rest of what the client was told about that level, and
`world.server()` what it was told about the server's worlds when it joined the server. Both models are in
`me.whereareiam.anvil.capability.world.model`:

```java
LevelInfo level = world.level().orElseThrow();
assertEquals("minecraft:the_nether", level.getType().getKey());
assertEquals(Environment.NETHER, level.getType().getEnvironment());
assertEquals(LevelGeneration.NORMAL, level.getGeneration());

ServerInfo server = world.server().orElseThrow();
assertTrue(server.getLevels().contains("minecraft:arena"));
```

| Member | Meaning |
| --- | --- |
| `LevelInfo.dimension` | The level's key, the same as `world.dimension()`. |
| `LevelInfo.type.key` | The key of the level's dimension type, such as `minecraft:the_nether` or a data pack's `example:void`. |
| `LevelInfo.type.environment` | How the client renders the level, from its dimension type's sky and shading: `OVERWORLD`, `NETHER`, `END`, or `CUSTOM` for any other pair, such as a mod's. A world a plugin created as a nether world has the environment `NETHER`. |
| `LevelInfo.generation` | `NORMAL`, `FLAT` for a superflat world, or `DEBUG` for a debug world. |
| `ServerInfo.hardcore` | Whether the server is in hardcore mode. |
| `ServerInfo.levels` | The keys of every level the server has, in the server's order. |

The level changes with every join and respawn, and each `DimensionChange` carries the `LevelInfo` of the
level it entered as `getLevel()`. The server info changes only when the player joins a server, which a
proxy does when it moves the player to another one. `world.changes()` lists every change in order, across
reconnects.

The game mode is not part of either: a server changes it without a respawn, so `World` does not report it.

## Expect that the player stays

```java
dimensions.neverEntered("minecraft:the_nether", Duration.ofSeconds(2));
```

`neverEntered` fails as soon as the player has entered the dimension since the previous wait, and
returns once the duration has passed without that. `expectChanged(matcher)` gives an expectation for
`alice.awaitFirst(...)`, and `unread()` lists the changes since the previous wait.
