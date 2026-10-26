---
title: Movement
description: Place and turn a player, or let the vanilla client's physics walk, jump and fall with the keys it holds.
---

`Movement` controls where a simulated player is and which way it looks. Without client physics, which is
the default, the player stays where the server placed it and moves only when the test sends a position.
With client physics on, the player's client moves itself as the vanilla client does: it falls, lands,
walks, sprints, sneaks, jumps, swims and climbs with the keys the test holds, and tells the server where it
is in the same packets and at the same moments as the vanilla client of its release. Install the movement
unit and its Session dependency, or explicitly select the default capability unit.

Whichever way the player moves, its client answers every server teleport as the vanilla client does: it
accepts the teleport and at once sends the position, view direction and ground state `false` the teleport
left it at, also when the teleport is relative to where it was. From Minecraft 26.3 the acceptance itself
carries that position, as the vanilla client sends it.

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
alice.capability(Movement.class).move(Position.builder()
		.x(0.15)
		.y(64)
		.z(0)
		.yaw(90)
		.pitch(0)
		.onGround(true)
		.build());
```

Anvil sends the requested update; the server decides whether to accept it. An invalid position or a
large jump can cause a correction or kick. `look(yaw, pitch)` turns the view without moving, and
`position()` returns where the client is: where the server last placed it and where the test moved it
since.

## Walk with client physics

Turn physics on, hold keys, and wait for the client's ticks. The client runs 20 ticks a second while it is
connected and in a world:

```java
import me.whereareiam.anvil.capability.movement.model.MovementInput;

Movement movement = alice.capability(Movement.class);
movement.physics(true);
movement.input(MovementInput.builder().forward(true).sprint(true).build());
movement.awaitTicks(40);
movement.input(MovementInput.NONE);
Position reached = movement.position();
```

- `physics(true)` may be called before the player connects and stays on across reconnects. The client
  starts moving once the server placed it on a connection; a login and a respawn forget the position until
  the next placement.
- `input(...)` holds the keys from the next tick on: `forward`, `backward`, `left`, `right`, `jump`,
  `sneak` and `sprint`. Holding jump jumps whenever the player stands on the ground and its jump delay of
  ten ticks has passed; holding sprint sprints while the player walks forward, as the sprint key does.
- `look(yaw, pitch)` turns the view, which the client reports with its next tick; the player walks the
  way it looks.
- `awaitTicks(n)` waits until the client has run `n` more ticks, by default for five seconds plus twice
  the time the ticks take; `awaitTicks(n, timeout)` takes its own timeout.
- `position()` and `motion()` read where the client is and its velocity in blocks per tick.
- `move(position)` places the client there with no velocity, and physics continues from there.

The client moves only while the chunk it is in is loaded, as the vanilla client does: in an unloaded chunk
it neither moves nor counts ticks, and from Minecraft 1.21.2 it still ends each tick.

### What the client reports

After each of its ticks the client sends what the vanilla client of its release sends: from 1.21.2 the keys
it holds when they changed, sprinting and, before 1.21.6, sneaking as player commands when they changed,
and one movement packet: the position when it moved more than 0.0002 blocks or every 20 ticks, the
rotation when it changed, both in one packet, or only the ground state when only that changed. From 1.21.2
every movement packet carries whether the player pushes against a block horizontally, and each tick ends
with the end of the client tick.

### What the physics simulates

The client simulates the vanilla client's own player with the block data of its release: gravity and air
drag, block friction (ice, slime, blue ice), walking, sprinting with its jump boost, sneaking with its lower
box and its stop at edges, jumping with its delay and the jump factor of honey, stepping up 0.6 blocks,
collisions with each block's collision shape one axis at a time, the ground and horizontal collision flags,
the speed factor of soul sand and honey, bouncing on slime blocks and beds, sliding down honey, slowing in
cobwebs, sweet berry bushes and powder snow, climbing ladders, vines and scaffolding (and open trapdoors
above a ladder), bubble columns, and swimming and sinking in water and lava with their currents. Releases
differ where the vanilla client does: Anvil follows each release's own arithmetic, so that a tick gives
the values the vanilla client of that release computes.

Client physics does not simulate:

- flying, gliding with an elytra, riding, swimming in the swimming pose and crawling;
- effects (speed, slowness, jump boost, slow falling, levitation, dolphin's grace), enchantments such as
  depth strider and soul speed, attributes the server changes, food too low to sprint, and the slowdown of
  using an item;
- collisions with entities, such as boats and shulkers, and with the world border;
- the vanilla client's push out of blocks it is stuck in;
- shapes that depend on the entity or the position: scaffolding and powder snow use one shape, and bamboo
  and pointed dripstone their unmoved shape.

Minecraft versions Anvil ships no block data for, such as 26.2 and 26.3, have no client physics:
`physics(true)` throws `IllegalStateException` naming the reason. Moves, turns and teleport answers work
on every release.

## Assert the effect you need

After a move, wait for the behavior under test: a region-entry message, a movement restriction response,
or a position observation exposed by an [agent operation](../../../../extending/agent-operations/index.md).
The `move` call returning does not establish that the server accepted the position or that a plugin event
completed. The capability does not find paths or navigate on its own: drive the keys and the view from
your Java journey.
