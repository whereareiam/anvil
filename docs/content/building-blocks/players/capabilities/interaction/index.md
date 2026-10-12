---
title: Interaction
description: Use held items and target known blocks or entities from a player journey.
---

`Interaction` sends item use, block interaction, and entity interaction actions. Install the interaction
unit and Session dependency, or explicitly select the default capability unit. Your fixture supplies the world, reachable
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

## Bedrock players

A Bedrock player sends each use as an item use transaction from the hotbar slot it holds, with the item
that slot or the off hand holds and its position at the eyes, and swings its arm with an animate packet.
`useItem` uses the item in the air. `entity` sends `INTERACT` or `ATTACK`, the two entity actions Bedrock
has, for the entity's runtime ID.

`block` names the block it targets by the block's runtime ID, which the player knows only from the world
the server sent it. Until the player has received the targeted block, `block` fails with a message that
says so.

The off hand is part of Bedrock's item use from Bedrock 1.26.50 and of its arm swing and entity
interaction from 1.26.60. On older versions an action with `Hand.OFF` fails instead of acting with the
main hand; `useItem` swings the arm first, so it needs 1.26.60 for the off hand as well.
