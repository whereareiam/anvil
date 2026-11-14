---
title: World
description: Read the dimension the player is in, its type, environment and generation, the server's worlds, and the time, weather, world border and spawn point its client knows, and wait until they change.
---

`World` reports the dimension the player's client is in: the key of the level the server put it in, with
its namespace, such as `minecraft:overworld`, `minecraft:the_nether` or, for a world a plugin created
under the name `arena`, `minecraft:arena`. A client learns its dimension when it joins a level and each
time the server respawns it, which is also how a server moves a player to another dimension; the client
never picks one itself. It also reports what the client is told about the world around its level: the
time, the weather, the world border and the spawn point. Install the world unit and its Session
dependency, or explicitly select the default capability unit. Every MCProtocolLib release Anvil ships has
it.

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

## Read the time, weather, border and spawn point

The server tells the client the time, the weather, the world border of its level and the world's spawn
point as they change. `world.time()`, `world.weather()`, `world.border()` and `world.spawn()` return
what the client was told last, or empty while the player is in no level or the server has not sent it
yet. `world.states()` returns a `WorldStateWatch`, which waits until all four together, a `WorldState`,
satisfy a condition. These fragments belong in a live test after Alice has connected to the server
`lobby`. They use `ScenarioContext anvil`, `World world` and these imports:

```java
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.capability.world.WorldStateWatch;
import me.whereareiam.anvil.capability.world.type.WeatherType;
import java.time.Duration;
```

```java
WorldStateWatch states = world.states();
ProcessConsole console = anvil.processes().server("lobby").console();
console.sendCommand("weather thunder");
states.until(state -> state.weatherType() == WeatherType.THUNDER, Duration.ofSeconds(20));

console.sendCommand("time set noon");
states.until(state -> state.getTime() != null
		&& state.getTime().timeOfDay().orElse(-1) >= 6000, Duration.ofSeconds(10));
```

`until` checks the state now and every state the client is told afterwards, so a state that lasts only
between two checks still counts. `never(condition, duration)` fails as soon as a state satisfies the
condition during the duration, and `expect(condition)` gives an expectation for `alice.awaitFirst(...)`.

### Time

`WorldTime` (`me.whereareiam.anvil.capability.world.model`) has these members:

| Member | Meaning |
| --- | --- |
| `gameTime` | The age of the world in ticks, which advances whatever the time of day does. |
| `dayTime` | The time of day of the player's level in ticks since the world began, or `null` when the level has none. `timeOfDay()` gives the time within the current day, from `0` to `23999`, as `/time query daytime` does. |
| `advancing` | Whether the time of day advances; `false` while the server stops it or the level's clock is paused. |
| `clocks` | From Minecraft 26.1, the server's world clocks by key, each with its `ticks` and `rate`; empty before. |

Releases before Minecraft 26.1 send the time of day of the player's level. From 26.1 the server keeps
world clocks instead, and each dimension type names the clock its day follows: the overworld's types
follow `minecraft:overworld`, the end's `minecraft:the_end`, and the nether's none. `dayTime` is then
the time of the clock of the player's dimension type, so it is `null` in the nether on 26.1 and later,
while older releases report a time of day there.

### Weather

`Weather` has the `rainLevel` and `thunderLevel` from `0` to `1`, which the server raises and lowers
gradually, so rain and thunderstorms fade in and out. The client keeps no other weather: when the server
says rain starts or stops, it only sets the rain level to where the following changes begin. Paper
servers before Minecraft 26.2 send those two events the other way round, so wait on the levels or the
weather's type rather than on the moment rain starts. `type()` (and `WorldState.weatherType()`) tells the weather the client shows, as the game
judges it: `THUNDER` while the rain level is above `0.2` and the thunder level above `0.9`, `RAIN` while
only the rain level is, and `CLEAR` otherwise. A level the player enters is clear until the server says
otherwise.

### World border

`WorldBorder` has the `centerX` and `centerZ` of the border, its `size` as a side length in blocks, and,
when the server moves it over time, the `lerpFrom` size it started at and the `lerpTicks` it takes,
counted from when the client was told. It also has `absoluteMaxSize`, `warningBlocks` and
`warningTime`. Releases before Minecraft 1.21.9 move the border in milliseconds, which `World` reports
as ticks of 50 milliseconds. The border belongs to the level: it is empty after the player enters
another dimension until the server describes that dimension's border.

### Spawn point

`SpawnPoint` has the `dimension` of the spawn point's level, its block `x`, `y` and `z`, and the `yaw`
and `pitch` a player who spawns there looks at. Releases before Minecraft 1.21.9 do not name the level,
and `World` takes the dimension the player was in when the server told it, as the client does. They
send no pitch, and releases before Minecraft 1.17 no yaw; both are then `0`.
