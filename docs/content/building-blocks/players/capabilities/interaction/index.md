---
title: Interaction
description: Use held items and target known blocks or entities from a player journey.
---

`Interaction` sends item use, block interaction, and entity interaction actions. Install the interaction
unit and Session dependency, or use the umbrella plugin. Your fixture supplies the world, reachable
targets, and any items needed by the journey.

## Use the selected item

For a connected `SimulatedPlayer alice` holding the intended item, import
`me.whereareiam.anvil.capability.interaction.Interaction` and
`me.whereareiam.anvil.capability.interaction.type.Hand`:

```java
var interaction = alice.capability(Interaction.class);
interaction.useItem(Hand.MAIN);
```

Use [Inventory](../inventory/index.md) to select the hotbar slot first when needed. Observe your
plugin's resulting event or response; sending the interaction does not itself establish success.

## Target a prepared block

With a reachable fixture block at `(1, 64, 0)`, import
`me.whereareiam.anvil.capability.interaction.model.BlockPosition` and
`me.whereareiam.anvil.capability.interaction.type.BlockFace`, then use:

```java
interaction.block(
		BlockPosition.builder().x(1).y(64).z(0).build(),
		BlockFace.UP,
		Hand.MAIN
);
```

Replace these coordinates with the target created by your fixture. `BlockFace` selects the targeted
face; `Hand` selects `MAIN` or `OFF`. Set up the player's position so the server can accept the action.

## Target an entity

For an `int entityId` supplied by your fixture for an entity visible to this player:

```java
interaction.entity(entityId, EntityInteraction.INTERACT, Hand.MAIN);
```

Import `me.whereareiam.anvil.capability.interaction.type.EntityInteraction`. Use `ATTACK` for an attack
action. The identifier is the protocol entity ID, not a UUID, and must refer to the current entity
instance. This capability does not discover nearby entities or choose targets automatically.

Use [messages](../messages/index.md), [inventory observations](../inventory/index.md), or a custom
[agent operation](../../../../extending/agent-operations/index.md) to verify the application result.
