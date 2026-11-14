---
title: Moving players between worlds
description: List a server's worlds, find which world a player is in, move it to another one, and wait for the server to report the move.
---

Use a server's `Worlds` capability when a test needs what a server knows about its worlds, or needs a
player moved to another world as a plugin would move it. Retrieve it from the process:
`anvil.processes().server("lobby").capability(Worlds.class)`.

`Worlds` works through each platform's own API: Bukkit's on Paper and Spigot, and Minecraft's on
NeoForge, for every release Anvil supports. Its provider installs the agent extensions it needs into
those servers itself; nothing has to be declared in the scenario. A proxy has no worlds, so it has no
`Worlds`. Install the `me.whereareiam.anvil.capability.worlds` unit with the platform units, or
explicitly select the default capability unit, which includes it.

A world is named by the key of its level, with its namespace, such as `minecraft:the_nether`: the key the
player's client is told, which the player's [World](../../../players/capabilities/world/index.md)
capability reports. On Paper and Spigot, the main world and its nether and end have the vanilla keys, and
a world a plugin created under the name `arena` is `minecraft:arena`. A key without a namespace is in
the `minecraft` namespace.

## Move a player and follow the move

This fragment belongs in a live test after Alice has connected to `lobby`. It uses
`ScenarioContext anvil`, `SimulatedPlayer alice` and these imports:

```java
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.capability.worlds.WorldChangeWatch;
import me.whereareiam.anvil.capability.worlds.Worlds;
import me.whereareiam.anvil.capability.worlds.model.WorldLocation;
import java.time.Duration;
```

```java
Worlds worlds = anvil.processes().server("lobby").capability(Worlds.class);
WorldChangeWatch changes = worlds.watch();

worlds.send(alice, WorldLocation.builder()
		.world("minecraft:the_nether")
		.x(0.5).y(140).z(0.5)
		.build());

changes.changed(alice, "minecraft:the_nether", Duration.ofSeconds(10));
alice.capability(World.class).watch().entered("minecraft:the_nether", Duration.ofSeconds(10));
```

`send(player, world)` moves the player to the world's spawn, and `send(player, location)` to an exact
position and view. Both fail with `IllegalArgumentException` when the server has not loaded the world,
and with `IllegalStateException` when the player is not on the server or the server refused the move,
for example because a plugin cancelled the teleport. A spawn is where the server says players arrive: Bukkit
keeps one for each world, while a NeoForge server reports the position of its respawn world for every world,
which in the nether can be inside the rock, so pass a location there.

`changes.changed(player, world)` waits until the server reports the player moving to that world: a
teleport, a portal, or a respawn in another world. Joining the server is no world change. Create the
watch before the action. `changed(player, timeout)` waits for a move to any world,
`neverChanged(player, duration)` fails as soon as the server moves the player, and
`expectChanged(player, world)` gives an expectation for `alice.awaitFirst(...)`.

## Read the worlds and where a player is

`worlds.worlds()` lists the loaded worlds in the server's order, starting with its main world. Each
`ServerWorld` has its `key`, its `kind` (`OVERWORLD`, `NETHER`, `END`, or `CUSTOM` for a dimension type
of a data pack or mod), and its `spawn`. `worlds.world("arena")` finds one.

`worlds.location(alice)` is where the server says Alice is: the world, position and view, or empty while
she is not on the server. A player is found by every profile its client was granted, so a player that a
proxy forwarded under another UUID is still found.

A restarted server keeps the world changes reported before the restart, followed by those of its new
process.
