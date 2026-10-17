---
title: Sounds
description: Wait for the sounds a server plays to the player, named or by registry ID.
---

`Sounds` records every sound the server tells the player to play. Install the sounds unit and its Session
dependency, or explicitly select the default capability unit. Every MCProtocolLib release Anvil ships has
it.

## Wait for a sound

This fragment belongs in a live test after Alice has connected to a server whose plugin plays a sound. It
uses `SimulatedPlayer alice`, `Messages messages` and these imports:

```java
import me.whereareiam.anvil.capability.sounds.SoundWatch;
import me.whereareiam.anvil.capability.sounds.Sounds;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import java.time.Duration;
```

Start the watch before the action, then wait for the sound by its key:

```java
SoundWatch sounds = alice.capability(Sounds.class).watch();
messages.command("example ping");
HeardSound ping = sounds.heard("block.note_block.pling", Duration.ofSeconds(10));
```

A key without a namespace means `minecraft`, so `block.note_block.pling` and
`minecraft:block.note_block.pling` are the same sound, while a plugin's own sound keeps its namespace, such
as `example:alert`. Any predicate on the `HeardSound` works too:
`sounds.heard(sound -> sound.is("block.note_block.pling") && sound.getPitch() > 1F, timeout)`.

## Read a heard sound

| Property | Holds |
| --- | --- |
| `getKey()` | The namespaced key, such as `minecraft:entity.player.levelup` |
| `getCategory()` | The `SoundCategory`, the volume slider it belongs to, with vanilla names such as `PLAYERS` |
| `getVolume()`, `getPitch()` | Volume, where `1` is full, and pitch, where `1` is unchanged |
| `getPosition()` | Where a positioned sound plays, or `null` |
| `getEntityId()` | The entity a following sound plays at, or `null` |

Servers refer to built-in sounds by their registry ID and to other sounds by name; both arrive with their
key. Sounds a client plays on its own, such as footsteps or the sound of a block it breaks, never reach it
from the server and are not recorded.

## Expect that a sound is not played

`neverHeard(...)` watches for a duration and fails as soon as a matching sound has been heard since the
previous wait, naming its key:

```java
sounds.neverHeard("entity.villager.no", Duration.ofSeconds(2));
```

It returns normally once the duration passes without one and does not move the watch.
`expectHeard(matcher)` describes the next matching sound for `player.awaitFirst(...)`; see
[waiting for whichever outcome comes first](../../../../workflows/testing/assertions/index.md#wait-for-whichever-outcome-comes-first).

## Inspect failures

`history()` returns an immutable snapshot of every sound heard so far, and `unread()` lists what the watch
has not passed yet. Include them in a failed assertion to see whether the server played another sound, at
another pitch, or nothing at all.
