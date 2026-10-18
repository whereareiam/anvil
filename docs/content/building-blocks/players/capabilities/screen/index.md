---
title: Screen
description: Wait for the titles, action bars and boss bars a server shows the player.
---

`Screen` records what the server draws over the player's game view besides chat: titles with their
subtitles, action bars and boss bars. Install the screen unit and its Session dependency, or explicitly
select the default capability unit. Every MCProtocolLib release Anvil ships has it.

Each kind keeps its own history, in order and across reconnects, and hands out its own watch. Text is
flattened to plain text the same way [Messages](../messages/index.md#read-received-text) flattens chat.

## Wait for a title

This fragment belongs in a live test after Alice has connected to a server whose plugin shows a title. It
uses `SimulatedPlayer alice`, `Messages messages` and these imports:

```java
import me.whereareiam.anvil.capability.screen.Screen;
import me.whereareiam.anvil.capability.screen.TitleWatch;
import me.whereareiam.anvil.capability.screen.model.Title;
import java.time.Duration;
```

Start the watch before the action, then wait for the title:

```java
Screen screen = alice.capability(Screen.class);
TitleWatch titles = screen.watchTitles();
messages.command("example announce");
Title title = titles.shown("Announcement", Duration.ofSeconds(10));
```

`shown(text)` matches a title whose title or subtitle contains the text. Any predicate on the `Title`
works too, so one wait can require both lines:
`titles.shown(shown -> shown.getTitle().contains("Announcement") && shown.getSubtitle().contains("Alice"), timeout)`.
A `Title` also carries the fade-in, stay and fade-out ticks it was shown with.

A title is recorded when its text arrives, together with the subtitle shown under it. A subtitle shows only
with a title, as on a client: a server that sends the subtitle first produces one record with both lines;
a server that sends it after the title produces a record without and then one with the subtitle. A title stays for its times, so a subtitle arriving after
it has faded is kept for the next title.

## Wait for an action bar

```java
ActionBarWatch actionBar = screen.watchActionBars();
messages.command("example follow Bob");
actionBar.shown("+1 follower", Duration.ofSeconds(10));
```

Import `me.whereareiam.anvil.capability.screen.ActionBarWatch`. The action-bar history holds the text a
server sends through the action-bar packet, which the vanilla `title <player> actionbar` command uses, and
the action-bar text it sends as chat, which is game-info chat before Minecraft 1.19 and overlay system chat
from 1.19. Either way it is an action bar, never part of [Messages](../messages/index.md).

## Follow a boss bar

A boss bar is shown when the server adds it and each time the server changes its text, progress, style or
flags. `shown` finds the first addition or change after the previous wait that leaves the bar in the state
you need, and `removed` the moment it leaves the screen:

```java
BossBarWatch bossBars = screen.watchBossBars();
messages.command("example event start");
BossBar event = bossBars.shown(bar -> bar.getText().contains("Event") && bar.getProgress() >= 0.5F,
		Duration.ofSeconds(10));
bossBars.removed(bar -> bar.getId().equals(event.getId()), Duration.ofSeconds(60));
```

Import `me.whereareiam.anvil.capability.screen.BossBarWatch` and
`me.whereareiam.anvil.capability.screen.model.BossBar`. A `BossBar` has the server's ID for the bar, its
text, its progress from `0` to `1`, its `BossBarColor` and `BossBarOverlay` with vanilla names, and its
sky, music and fog flags. `screen.bossBars()` lists the bars on the screen now, in the order they were
added.

A bar belongs to one connection: when the connection ends, every bar it showed is recorded as removed, so
a new connection starts without bars, as on a client.

## Expect that something is not shown

Each watch has `neverShown(...)`, which watches for a duration and fails as soon as a matching title,
action bar or boss bar has been shown since the previous wait, naming it:

```java
titles.neverShown("Error", Duration.ofSeconds(2));
```

It returns normally once the duration passes without one and does not move the watch. Choose the shortest
duration in which the server would have shown it.

## Expect one among other outcomes

`expectShown(matcher)` on every watch, and `expectRemoved(matcher)` on a boss bar watch, describe the next
matching change without waiting. Pass them to `player.awaitFirst(...)` together with other expectations;
see [waiting for whichever outcome comes first](../../../../workflows/testing/assertions/index.md#wait-for-whichever-outcome-comes-first).

## Inspect failures

`titles()`, `actionBars()` and `bossBarEvents()` return immutable snapshots of the whole histories, and
each watch's `unread()` lists what arrived since its previous wait. Include them in a failed assertion to
see whether the server showed other text or nothing at all.
