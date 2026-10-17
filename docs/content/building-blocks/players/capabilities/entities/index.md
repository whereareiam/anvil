---
title: Entities
description: Follow the entities the server shows a player, wait for them to appear or disappear, and read what they show.
---

`Entities` tracks the entities the server shows the player, as the Minecraft client knows them: their
spawns and removals, where they are and which way they face, what rides what, their custom names and
text-display text, the item they show, and the flags a client sees, such as invisible, glowing, burning
and sneaking. Use it to check what a player sees that is neither chat nor a container: a mob or an item
a command spawned, a hologram, another player sneaking, or the name bubble a plugin shows above a player
who chats. Install the
entities unit and its Session dependency, or explicitly select the default capability unit.

Every MCProtocolLib release Anvil ships has the capability. Text displays arrived in Minecraft 1.19.4,
so players on 1.18.2 see armor stands and other entities with custom names, but no text displays.

## Wait for text above the player

This fragment belongs in a live test after Alice has connected to a server whose plugin shows what a
player says above their head. It uses `SimulatedPlayer alice`, her `Messages`, and these imports:

```java
import me.whereareiam.anvil.capability.entities.Entities;
import me.whereareiam.anvil.capability.entities.EntityQuery;
import me.whereareiam.anvil.capability.entities.EntityWatch;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import java.time.Duration;
```

Start a watch before the action, then wait for the bubble and for it to go away:

```java
Entities entities = alice.capability(Entities.class);
EntityWatch bubbles = entities.watch();
messages.chat("hello");
SeenEntity bubble = bubbles.appeared(EntityQuery.labelled("hello").and(EntityQuery.aroundPlayer(4)),
		Duration.ofSeconds(5));
bubbles.disappeared(bubble, Duration.ofSeconds(10));
```

A watch sees only the changes made after it was created. An entity appears for it when a change makes the
entity satisfy the condition: it spawns already satisfying it, or a later change does, such as the
custom name a server sends just after the spawn. Changes are facts about an entity: it spawned, it was
removed, or what it shows or rides changed. Moves and turns are not changes; each change carries the
position the entity had at that moment. An entity that already satisfied the condition before
the watch does not appear by staying the same. `appeared(...)` returns the entity as it was at that
moment, and the following wait continues after it, so two bubbles with the same text are found one per
wait.

`disappeared(entity, timeout)` waits until Alice no longer knows that entity: the server removed it, she
changed world, or her connection ended. It returns at once for an entity that is already gone and does
not move the watch, so a bubble that appeared in between is still found afterwards.

`neverAppeared(condition, duration)` fails as soon as a matching entity has appeared since the previous
wait and returns normally once the duration has passed without one; it does not move the watch.
`expectAppeared(condition)` and `expectDisappeared(entity)` describe the same waits without waiting, for
[`player.awaitFirst(...)`](../../../../workflows/testing/assertions/index.md#wait-for-whichever-outcome-comes-first).

## Wait for an entity where it is now

A watch cannot see an entity walk up to Alice, because moves are not changes. `present(condition,
timeout)` reads every entity Alice knows as it is now, on each check, and returns the first that
satisfies the condition, at once if one already does:

```java
SeenEntity villager = entities.present(EntityQuery.ofType("villager").and(EntityQuery.nearPlayer(3)),
		Duration.ofSeconds(20));
```

`entities.all()` returns the same current state without waiting.

## Describe the entity

A condition is any `Predicate<SeenEntity>`. `EntityQuery` builds the common ones and names them in the
failure of a wait, for example `Player did not see an entity appear labelled 'hello' and within 4.0 blocks
of or riding the player`:

| Query | Matches an entity |
| --- | --- |
| `labelled(text)` | whose custom name or text-display text contains the text |
| `ofType(type)` | of a type, such as `armor_stand` or `minecraft:text_display` |
| `nearPlayer(distance)` | at most that many blocks from Alice |
| `ridingPlayer()` | riding Alice, directly or on top of another entity that does |
| `aroundPlayer(distance)` | near Alice or riding her |
| `matching(description, condition)` | that satisfies any condition, under your description |

Combine them with `and`, `or` and `negate`, also with a plain predicate:

```java
SeenEntity stand = bubbles.appeared(EntityQuery.ofType("armor_stand").and(SeenEntity::isInvisible),
		Duration.ofSeconds(5));
```

`aroundPlayer` suits text that rides the player: the server sends no positions for a passenger, so its
position stays where it spawned while it rides along.

## What an entity tells

`SeenEntity` holds what the client knows about the entity:

- `getId()`, the entity ID the server gave it, which [Interaction](../interaction/index.md) uses to interact
  with it, `getUniqueId()` and `getType()`, such as `minecraft:armor_stand`;
- `getX()`, `getY()` and `getZ()`, where the server last placed it, and `getYaw()` and `getPitch()`,
  which way it faces;
- `getCustomName()`, `isCustomNameVisible()` and `getText()`, a text display's text, each flattened to
  plain text like a chat message;
- `getItem()`, the item a dropped item, item frame, item display, thrown item or firework shows, with
  its protocol ID and amount; protocol IDs depend on the release, as in [Inventory](../inventory/index.md);
- `isInvisible()`, `isGlowing()`, `isOnFire()`, `isSneaking()`, `isSprinting()`, `isSwimming()` and
  `isGliding()`, the flags every entity carries;
- `getVehicleId()`, `getPassengerIds()` and `isRidingPlayer()`;
- `getPlayerDistance()`, the distance from Alice when the value was taken, or `null` before the
  server placed her;
- `label()`, the custom name or else the display text.

Positions, rotations and distances come from the client's current state: `all()` and `present()` read
them as they are now, and a change carries them as they were when it happened.

## Entities and connections

Each connection starts with no entities. When Alice reconnects, every entity of the previous connection
disappears, and `getConnection()` numbers the connection an entity belongs to, so an entity ID that the
server reuses on the next connection names another entity. A respawn or a change of world also makes
every entity disappear, as it does in the client.

## Inspect failures

`entities.all()` returns the entities Alice knows now, without waiting, and `watch.unread()` returns
every change since the watch's previous wait, each with the entity before and after it. When a wait for
an entity fails, check whether the entity spawned at all, whether its text is in the custom name or the
display text, and how far from Alice it is.
