---
title: Vitals
description: Read the player's health and food, wait for its deaths and respawn it.
---

`Vitals` reports the player's own health, food and deaths as its client knows them, and respawns it after
a death as the death screen's button does. Install the vitals unit and its Session dependency, or
explicitly select the default capability unit. Every MCProtocolLib release Anvil ships has it.

## Read health and food

This fragment belongs in a live test after Alice has connected. It uses `SimulatedPlayer alice` and these
imports:

```java
import me.whereareiam.anvil.capability.vitals.DeathWatch;
import me.whereareiam.anvil.capability.vitals.Vitals;
import me.whereareiam.anvil.capability.vitals.model.Death;
import java.time.Duration;
```

```java
Vitals vitals = alice.capability(Vitals.class);
float health = vitals.health();
int food = vitals.food();
float saturation = vitals.saturation();
```

`health()` is in half hearts, `20` for a full bar without bonus health, and `food()` in half drumsticks,
from `0` to `20`. The values are the ones the server last sent the player's connection; each new
connection starts with the values a client starts with, `20`, `20` and a saturation of `5`, until the
server sends its own.

## Wait for a death and respawn

Start the watch before the action, then wait:

```java
DeathWatch deaths = vitals.watchDeaths();
messages.command("arena fight");
Death death = deaths.died(Duration.ofSeconds(10));
assertTrue(vitals.dead());
vitals.respawn();
assertEquals(20F, vitals.health());
```

A `Death` carries the death screen's message, flattened to plain text as Messages flattens chat: a
translated message keeps its key and arguments, such as `death.attack.genericKill Alice`. Like the
game's client, the player stays dead until the test calls `respawn()`, which sends the respawn request
and waits until the server reports the player alive again. It fails when the player is not dead.

`died(matcher, timeout)` waits for a death a predicate accepts, `neverDied(duration)` fails as soon as
the player has died since the previous wait, `expectDied(matcher)` gives an expectation for
`alice.awaitFirst(...)`, and `unread()` lists the deaths since the previous wait. `vitals.deaths()` lists
every death in order, across reconnects.

`dead()` is true from the moment the server reports the death, or while the health is zero or less,
until the server reports the player alive. A player who left while dead joins dead again without a new
death; respawn it the same way.

## Immediate respawn

When the server turns the death screen off with the `doImmediateRespawn` game rule (`immediate_respawn`
from Minecraft 1.21.11), the client respawns by itself as soon as it dies, as the game's client does.
The death is still recorded, with `isRespawnedImmediately()` true, and `dead()` stays false.

## Players do not fall by themselves

Anvil's client sends no movement of its own, only the moves a test makes through
[Movement](../movement/index.md), and servers move a player only by what its client sends. A player
that joins above the ground stays there and takes no fall damage. When a player dies right after joining,
something on the server killed it; watch its deaths to see the message.
