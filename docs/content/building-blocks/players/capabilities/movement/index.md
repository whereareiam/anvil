---
title: Movement
description: Send an absolute player position and view update and verify its application effect.
---

`Movement` sends an absolute position, rotation, and on-ground flag. Install the movement unit and
Session dependency, or explicitly select the default capability unit. Connect the player before sending updates.

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

Replace the fixture coordinates with a known position supplied by your application test setup.
Anvil sends the requested update; the server decides whether to accept it. An invalid position or a
large jump can cause a correction or kick.

## Bedrock players

Bedrock positions a player by its eyes, so a Bedrock player sends the position 1.62 blocks above the
feet the `Position` names; pass the same feet position as for a Java player. Under server
authoritative movement, which every Bedrock version from 1.21.90 uses and which Lumi turns on by
default, the update is an authoritative input of the player's current tick that presses no movement
key. The server decides whether the player stands on the ground, so `onGround` has no effect there.
When an older server grants the client authority over movement, the update is a player move that
carries `onGround`.

After a teleport, Lumi accepts the next update only within about 0.3 blocks of the position it set
and resets the player otherwise, and it ignores updates for the half second after a teleport. Start
from a position you know exactly, such as one your test set with `tp`, and move in small steps.

## Assert the effect you need

After the update, wait for the behavior under test: a region-entry message, a movement restriction
response, or a position observation exposed by an [agent operation](../../../../extending/agent-operations/index.md).
The `move` call returning does not establish that the server accepted the position or that a plugin
event completed.

The capability does not expose a position getter, pathfinding, walking simulation, or autonomous
navigation. Supply deliberate updates from your Java journey. If exact initial coordinates matter,
obtain or set them through a fixture before issuing movement.
