---
title: Movement
description: Send an absolute player position and view update and verify its application effect.
---

`Movement` sends an absolute position, rotation, and on-ground flag. Install the movement unit and
Session dependency, or use the umbrella plugin. Connect the player before sending updates.

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

## Assert the effect you need

After the update, wait for the behavior under test: a region-entry message, a movement restriction
response, or a position observation exposed by an [agent operation](../../../../extending/agent-operations/index.md).
The `move` call returning does not establish that the server accepted the position or that a plugin
event completed.

The capability does not expose a position getter, pathfinding, walking simulation, or autonomous
navigation. Supply deliberate updates from your Java journey. If exact initial coordinates matter,
obtain or set them through a fixture before issuing movement.
