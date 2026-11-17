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
water and lava, and sends the movement packets that client sends.

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
- its attributes, such as movement speed, jump strength, gravity and step height;
- its effects, such as jump boost, levitation, slow falling and dolphin's grace;
- the velocity the server sets, such as a knockback, and every teleport;
- its death: a dead player holds no keys and sends no movement until it respawns.

The blocks come from the chunks the player's client keeps, named by the block data Anvil ships for every
supported protocol. `enable()` throws `CapabilityUnavailableException` with the reason for a release it
cannot simulate. A tick that fails turns physics off; `physics.failure()` then tells why.

Movement does not choose where to go: it has no pathfinding or autonomous navigation. Hold the keys and turn
the view from your Java journey.

### Limits

Physics reproduces the vanilla client's own movement of its body. It does not yet reproduce:

- collisions with other entities, and the world border;
- sprint-swimming, gliding with an elytra, riding vehicles, using items, and auto-jump;
- what equipment changes before Minecraft 1.21, where it is not an attribute: depth strider, soul speed,
  swift sneak, and leather boots on powder snow;
- blocks the server added that are not vanilla's, such as a modded server's: a tick that meets one turns
  physics off with the reason.

Its numbers were checked against the vanilla client of Minecraft 1.21.1, 1.21.11 and 26.1.2; the other
supported versions follow the same rules with the differences their releases are known to have.
