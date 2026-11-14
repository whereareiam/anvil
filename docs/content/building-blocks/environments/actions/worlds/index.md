---
title: Moving players between worlds
description: List a server's worlds, create, load, unload and delete worlds, wait for the server to load or unload one, find which world a player is in, move it to another one, and wait for the server to report the move.
---

Use a server's `Worlds` capability when a test needs what a server knows about its worlds, needs a
world created, loaded, unloaded or deleted, waits for a plugin to load a world, or needs a player moved to
another world as a plugin would move it. Retrieve it from the process:
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

## Create, unload and delete worlds

A test that needs a world the server does not have creates it, as a plugin creating one through
Bukkit's `WorldCreator` does. This fragment uses the imports above and these:

```java
import me.whereareiam.anvil.capability.worlds.model.ServerWorld;
import me.whereareiam.anvil.capability.worlds.model.WorldCreation;
import me.whereareiam.anvil.capability.worlds.type.WorldGenerator;
import me.whereareiam.anvil.capability.worlds.type.WorldKind;
```

```java
ServerWorld arena = worlds.create(WorldCreation.builder()
		.key("arena_nether")
		.kind(WorldKind.NETHER)
		.seed(42L)
		.generator(WorldGenerator.FLAT)
		.build());

worlds.send(alice, arena.getKey());
changes.changed(alice, "minecraft:arena_nether", Duration.ofSeconds(10));

worlds.send(alice, "overworld");
changes.changed(alice, "minecraft:overworld", Duration.ofSeconds(10));
worlds.unload("arena_nether");
```

A `WorldCreation` names the world's `key`, and optionally its `kind` (`OVERWORLD` by default, `NETHER`
or `END`), its `seed` (one the server picks by default) and its `generator`: `DEFAULT` for the kind's
normal terrain, `FLAT` for a superflat world, or `EMPTY` for a world without blocks whose spawn is the block
at `0, 64, 0`, where a player falls until the test places something under it. `create` returns the
world as the server reports it, and fails with `IllegalArgumentException` when the server has already
loaded a world of that key or the kind is `CUSTOM`.

`unload(world)` saves the world and unloads it. It fails with `IllegalArgumentException` when the server
has not loaded the world, and with `IllegalStateException` when the server refuses: for its main world,
for a world players are still in, so send them elsewhere first, or when a plugin keeps the world loaded.

`delete(world)` unloads the world without saving it and deletes its folder from the server's
workspace, so creating the same key again starts a new world. It refuses what `unload` refuses, and
fails with `IllegalStateException` when the server unloaded the world but its folder could not be
deleted.

## Load a prepared map

`load(world)` loads a world from the folder the server keeps for it: a prepared map the scenario copies
into the server's workspace, or a world the test unloaded earlier. Unlike `create`, it never generates
a new world, and fails with `IllegalArgumentException` when the server keeps no folder for the world or
has already loaded it.

```java
ServerWorld arena = worlds.load("arena");
ServerWorld lair = worlds.load("lair", WorldKind.NETHER);
```

Paper and Spigot look for the folder named after the key's path in the server's worlds' folder, such as
`arena`, or, since Minecraft 26.1, among the dimensions of the main world, such as
`world/dimensions/minecraft/arena`. The server does not read a world's kind from its folder, so pass the
kind of a nether or end map; `load(world)` loads an overworld.

## Keep worlds from leaking into later tests

A created world stays until the test unloads or deletes it, or the server stops, so a server that a
scenario keeps for later scenarios keeps it as well. `unload` keeps the world's folder, and creating the
same key again on that server loads what it kept. A test on a kept server deletes the worlds it created
when it is done, in a `finally` block when a failure must not leave them behind:

```java
try {
	worlds.create(WorldCreation.builder().key("arena").build());
	// ...
} finally {
	worlds.delete("arena");
}
```

## Wait for the server to load a world

A plugin may create a world on its own, such as the target of a teleport command that does not exist
yet. `watchWorlds()` follows the server loading and unloading its worlds from the moment the watch is
created:

```java
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.worlds.WorldEventWatch;
```

```java
WorldEventWatch events = worlds.watchWorlds();
alice.capability(Messages.class).command("tpworld arena_nether");

events.loaded("minecraft:arena_nether", Duration.ofSeconds(10));
changes.changed(alice, "minecraft:arena_nether", Duration.ofSeconds(10));
```

`loaded(world)` and `unloaded(world)` wait for the server to load or unload a world, whether a plugin or
the test caused it, and return a `WorldEvent` with the world's key, its `type` (`LOADED` or `UNLOADED`)
and the time. `neverLoaded(world, duration)` fails as soon as the server loads the world,
`expectLoaded(world)` gives an expectation for `alice.awaitFirst(...)`, and `unread()` returns the
events since the previous wait. The worlds a server loads while it starts are no events. This works on
NeoForge too, for levels a mod adds while the server runs.

## Read the worlds and where a player is

`worlds.worlds()` lists the loaded worlds in the server's order, starting with its main world. Each
`ServerWorld` has its `key`, its `kind` (`OVERWORLD`, `NETHER`, `END`, or `CUSTOM` for a dimension type
of a data pack or mod), and its `spawn`. `worlds.world("arena")` finds one.

`worlds.location(alice)` is where the server says Alice is: the world, position and view, or empty while
she is not on the server. A player is found by every profile its client was granted, so a player that a
proxy forwarded under another UUID is still found.

A restarted server keeps the world changes reported before the restart, followed by those of its new
process.

## Limitations

- Only Paper and Spigot create, load, unload and delete worlds. NeoForge has no API to create, load or
  unload a level while the server runs, so on NeoForge all four fail with
  `UnsupportedOperationException`. A NeoForge test that needs another dimension adds it with a data pack
  or mod before the server starts.
- Bukkit creates a world under a plain name and names its level after it in the `minecraft` namespace,
  so a created or loaded key is `minecraft:<name>`, without `/`. Another namespace fails with
  `UnsupportedOperationException`.
