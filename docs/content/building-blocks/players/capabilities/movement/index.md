---
title: Movement
description: Place a player and turn its view, or let it move as the vanilla client does with client physics, the keys a test holds and the server's state.
---

`Movement` places a simulated player and turns its view, and runs the vanilla client's physics for it.
Install the movement unit and its Session dependency, or explicitly select the default capability unit.
Connect the player before moving it.

A player starts without physics: it goes exactly where the test moves it and stays there. With client
physics on, it moves as a vanilla client of its Minecraft version does: it falls, walks, sprints, sneaks and
jumps with the keys the test holds, collides with the blocks its world knows, steps up, climbs, moves through
water and lava, and sends the movement packets that client sends. Before Minecraft 1.19, whose default options
turn auto-jump on, it also jumps onto a block it walks into, as that client does.

## Send a controlled movement

The following fragment belongs in a live test with connected `SimulatedPlayer alice`. It assumes your
fixture has placed the player at `(0, 64, 0)` on solid ground and that moving slightly along X is valid
in the prepared world. Import:

```java
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Position;
```

Then send:

```java
Movement movement = alice.capability(Movement.class);
movement.move(Position.builder()
		.x(0.15)
		.y(64)
		.z(0)
		.yaw(90)
		.pitch(0)
		.onGround(true)
		.build());
movement.look(180, 10);
```

Anvil sends the requested update; the server decides whether to accept it. An invalid position or a
large jump can cause a correction or kick. `move` returning does not establish that the server accepted
the position or that a plugin event completed: wait for the behaviour under test.

`movement.position()` reads where the client is now: where the test last moved it, where physics moved
it, or where the server last placed it. It is empty before the server first placed the player on its
current connection.

## Move as the vanilla client does

Turn client physics on, hold keys, and wait for ticks. Import:

```java
import me.whereareiam.anvil.capability.movement.ClientPhysics;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
```

Then:

```java
ClientPhysics physics = alice.capability(Movement.class).physics();
physics.enable();
physics.input(MovementInput.builder().forward(true).sprint(true).build());
physics.awaitTicks(40);
physics.input(MovementInput.NONE);
```

Physics ticks twenty times a second and starts from the client's current position. `input` sets the keys
held from the next tick on: forward, backward, left, right, jump, sneak and sprint. `motion()` reads the
client's velocity in blocks per tick. `awaitTicks` waits until that many ticks have passed and fails at once
when physics is off.

Physics follows what the server tells the client, as the vanilla client does:

- its abilities and flying, so a creative or spectator player flies, and a spectator passes through blocks;
  when the player starts or stops flying itself, by pressing jump twice or by landing, it tells the server;
- its game mode, from the join, each respawn and each game mode change;
- the dimension type of its level, whose fast lava, as in the nether, pushes the player harder;
- its attributes, such as movement speed, jump strength, gravity and step height, and from 26.2 bounciness,
  friction modifier and air drag modifier;
- its effects, such as jump boost, levitation, slow falling and dolphin's grace;
- the velocity the server sets, such as a knockback, and every teleport;
- its death: a dead player holds no keys and sends no movement until it respawns.

The blocks come from the chunks the player's client keeps, named by the block data Anvil ships for every
supported protocol, which was read from each release's own vanilla client. A block collides, slows, bounces and
lets water and lava flow as that client says: scaffolding and powder snow by where the player's feet are, whether
it sneaks and how far it fell, and bamboo and pointed dripstone where their position moves them. `enable()`
throws `CapabilityUnavailableException` with the reason for a release it cannot simulate. A tick that fails
turns physics off; `physics.failure()` then tells why.

Every Java Edition player answers the server about its own position as the vanilla client does, whether it has
the Movement capability or not: its client accepts every teleport as it arrives with the position and view the
teleport leaves the player at, and before Minecraft 26.3 follows the acceptance with that position; from 1.21.2 it
ends each of its twenty ticks a second; and from Minecraft 1.21.4, after a join or a respawn, it waits for its
level: once the server started the level's loading and the chunk the player is in has loaded with the eight around
it, the player ticks and the client tells the server it loaded. Movement adds physics on top of that client: with
physics on, its tick moves the client's player within the client's tick, and `move` and `look` place and turn it.

Movement does not choose where to go: it has no pathfinding or autonomous navigation. Hold the keys and turn
the view from your Java journey.

### Limits

Physics reproduces the vanilla client's own movement of its body. It does not yet reproduce:

- collisions with other entities, and the world border;
- sprint-swimming, gliding with an elytra, riding vehicles, and using items;
- what equipment changes before Minecraft 1.21, where it is not an attribute: depth strider, soul speed and
  swift sneak; and leather boots, on which a vanilla player walks on and climbs out of powder snow in every
  release that has it;
- blocks the server added that are not vanilla's, such as a modded server's: a tick that meets one turns
  physics off with the reason.

Its numbers and the packets it sends are compared bit for bit, tick by tick, with reference traces of the real
vanilla client of every supported release, which the vanilla client recorded walking, sprinting, jumping, sneaking,
climbing, swimming and falling over the physics courses of the repository, joining a server whose terrain arrives
with the login or later, and being teleported, relative, absolute and with a velocity.
