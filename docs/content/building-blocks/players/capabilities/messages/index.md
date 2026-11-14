---
title: Messages
description: Send player chat or commands, await a message the player's chat shows, and check what the chat still holds.
---

`Messages` sends chat and commands as the connected player and records the chat messages the player sees, with
the colours, decorations and clicks of their parts. Install the messages unit and its Session dependency, or
explicitly select the default capability unit.

## Send and await a response

This fragment belongs in a live test after Alice has connected and joined `server`. It uses
`ScenarioContext anvil`, `SimulatedPlayer alice`, and these imports:

```java
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import java.time.Duration;
```

Send a message through the server console and wait for the player's chat to show it:

```java
Messages messages = alice.capability(Messages.class);
anvil.processes().server("server").console().sendCommand("say journey-ready-1");
Text response = messages.received("journey-ready-1", Duration.ofSeconds(10));
String text = response.plain();
```

A wait matches the message's plain text and returns the message as the chat showed it; `plain()` is its text,
and [its spans and components](#check-how-a-message-looks) tell how each part looks and what the server sent.

For your own plugin, send its command with `messages.command("your-command arguments")` and wait for
the reply it documents. Commands accept either a leading slash or no slash. Public chat uses
`messages.chat("text")`; it sends a chat message, not a console command. Before Minecraft 1.19 the
protocol has no command packet, so the server runs a `chat` text that starts with `/` as a command;
use `command(...)` whenever you mean a command, so the step behaves the same on every version.

## When the server refuses chat and commands

`chat(...)` and `command(...)` return once the player sent the message; the server decides what happens to it.
Three of its rules catch tests that send chat:

- A server refuses the chat and commands of a player that died and has not respawned. It runs nothing and shows
  the player a refusal instead, `chat.disabled.options` on current releases, one for each message. A real client
  respawns from the death screen, but a simulated player cannot respawn, and reconnecting does not help, because
  the server saves the player dead. Keep a player that sends chat alive: spawn it where it cannot fall or take
  damage, or protect it from your plugin when it joins.
- A server refuses the chat and commands that reach it while it moves the player to another dimension, with the
  same refusal. Paper takes the player out of its old level, loads the new one around it and only then puts it
  back, which takes a moment, longer the first time it generates the new level's chunks. A real client cannot type
  until it has entered the new level, so wait for the player to arrive before it sends anything:

  ```java
  DimensionWatch dimensions = player.capability(World.class).watch();
  console.sendCommand("execute in minecraft:the_nether run tp " + player.name() + " 0 120 0");
  dimensions.entered("minecraft:the_nether");
  messages.command("spawn");
  ```

- A server kicks a player who is not an operator for spam, with the reason `disconnect.spam`, when it sends more
  than about ten chat messages or commands within a second: each one adds 20 to a counter that drops by one every
  tick, and the server kicks at 200. Make the player an operator, wait for each reply before the next step, or run
  repeated setup through the console. `Session.state().kickReason()` names the kick, and the next send fails because
  the player is no longer connected.

The server runs a player's commands in the order they arrive, but Paper and Spigot handle chat on several threads, so
chat messages sent in quick succession may reach other players, and the sender's own chat, in another order. When a reply
does not arrive, look for a refusal in `history()` before you retry.

## Read what the chat showed

Each chat message the player's chat shows becomes one history entry: player chat, system messages and, on
Minecraft 1.19.4 and later, disguised chat such as `say` from the console. The history
follows the vanilla client's chat, so a message the client drops is not part of it, such as one that
arrives while the client still configures; a [failed wait names those](#inspect-failures).
Action-bar text is not chat, even when the server sends it as chat, and neither are titles;
[Screen](../screen/index.md) records them.

A message's plain text joins every part of the message in order, because the client's language file is not
available:

| Part | Contributes |
|---|---|
| Text | Its content |
| Translated | Its translation key, then each argument after a space |
| Keybind | The key name, such as `key.jump` |
| Selector | The pattern, such as `@p` |
| Score, NBT, or object (sprite, player head) | Nothing |

For example, on Paper or vanilla `1.18.2` a player's chat line arrives as `chat.type.text Alice hello`;
a server or plugin that formats chat itself sends its own text instead. On 1.19.4 and later, a
player-chat entry holds the message without the sender, `hello`, unless the server replaced its
content. Match on the text your server or plugin sends rather than on formatting that the client
would add. A plugin that translates its messages for each player sends the text of the player's
[game language](../../connections/index.md#set-the-games-language), which is `en_us` unless the player declares
another.

## Check how a message looks

A received message is a `Text`: the text component the server sent, with what each component contributes, the
style it sets and its children. Two views answer most questions. `spans()` is what the player sees: runs of text in
reading order, each in the style it inherits from the components around it. `find(...)` reaches the components
themselves, such as a translation and its arguments.

```java
import me.whereareiam.anvil.protocol.api.model.text.SpanStyle;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.model.text.TextColor;
import me.whereareiam.anvil.protocol.api.model.text.click.RunCommandClick;
import me.whereareiam.anvil.protocol.api.model.text.content.TranslatedContent;

Text welcome = chat.received("Welcome to Arbor", Duration.ofSeconds(10));
SpanStyle arbor = welcome.span("Arbor").orElseThrow().getStyle();
assertEquals(TextColor.GOLD, arbor.getColor());
assertTrue(arbor.isBold());
assertEquals(new RunCommandClick("/arbor help"), arbor.getClick());
```

`span(text)` returns the first span containing the text; text that crosses a change of style is in no span, so
look up a word that has one style. `spans()` lists them all for an exact comparison. A span's style holds:

| Member | Holds |
|---|---|
| `getColor()` | The colour, a `TextColor` such as `TextColor.GOLD` or `new TextColor(0x12ab34)`, or `null` for the chat's default colour |
| `isBold()`, `isItalic()`, `isUnderlined()`, `isStrikethrough()`, `isObfuscated()` | Each decoration, `false` unless the part or a part around it turns it on |
| `getClick()` | What a click does, such as `RunCommandClick`, `SuggestCommandClick`, `OpenUrlClick`, `CopyToClipboardClick` or `ChangePageClick`; `null` without a click |
| `getHover()` | What the game shows on hover: `ShowTextHover` with its `Text`, `ShowItemHover` or `ShowEntityHover`; `null` without one |
| `getShadowColor()`, `getFont()`, `getInsertion()` | The shadow colour (Minecraft 1.21.4 and later), the font's key and the text a shift-click inserts, or `null` |

A named colour and the same colour sent as `#rrggbb` are equal. A click on a dialog (`ShowDialogClick`) or a custom
action (`CustomClick`, by its identifier) keeps its payload in the player's worker, as the
[Dialogs](../dialogs/index.md) capability does.

### Check a translation

A server that leaves translation to the client sends a translation key with arguments. The player has no
language files, so its plain text and spans show the key followed by each argument, and `find` returns the
translation itself:

```java
Text joined = chat.received("multiplayer.player.joined", Duration.ofSeconds(10));
TranslatedContent translation = joined.find(TranslatedContent.class).orElseThrow();
assertEquals("multiplayer.player.joined", translation.getKey());
assertEquals("Ivan", translation.getArguments().getFirst().plain());
```

`find(type)` returns the first content of that kind in reading order, a translation's arguments before what follows
the translation. The kinds of content are in `me.whereareiam.anvil.protocol.api.model.text.content`:
`LiteralContent`, `TranslatedContent`, `KeybindContent`, `SelectorContent`, `ScoreContent`, `NbtContent`, and the
`SpriteContent` and `PlayerHeadContent` of Minecraft 1.21.9. `getContent()`, `getStyle()` and `getChildren()` walk
the components as the server sent them; a component's `TextStyle` holds only what it sets itself.

A player chat message holds the content its sender or the server sent, without the decoration the game draws
around it, such as the sender's name; content the server's chat signing protects arrives as literal text.

## Match the intended operation

`received(text)` matches messages containing the supplied text. It searches the whole history, so a
message received before the current operation can satisfy the wait. The bundled implementation keeps
that history across reconnects of the same player.

When an action repeats, [watch the messages](#watch-what-arrives-next) so that an earlier response
cannot satisfy the wait. For console assertions that must ignore earlier output, use a
[console watch](../../../../workflows/testing/assertions/index.md).

## Watch what the chat shows next

When a text repeats, such as a prompt shown again after a reconnect, start a watch before the action. A watch
sees only the messages the chat showed after it was created, and each wait continues after the message the
previous one found:

```java
MessageWatch chat = messages.watch();
messages.command("login secret");
Text welcome = chat.received("Welcome back", Duration.ofSeconds(10));
```

`received` also takes any predicate on the message's plain text, so one wait can require several texts in one
message or accept alternatives: `chat.received(text -> text.contains("Welcome") && text.contains("Alice"), timeout)`.
Import `me.whereareiam.anvil.capability.messages.MessageWatch`. `unread()` lists the messages shown since
the previous wait without moving the watch. Several watches on one player are independent.

## Expect that a message does not arrive

`neverReceived(...)` watches for a duration and fails as soon as the chat has shown a matching message since the
previous wait, naming it:

```java
chat.neverReceived("Invalid password", Duration.ofSeconds(2));
```

It returns normally when the duration passes without such a message, and it does not move the watch. Choose the
shortest duration in which the server would have sent the message.

## Expect a message among other outcomes

`chat.expectReceived(matcher)` describes the next matching message without waiting. Pass it to
`player.awaitFirst(...)` together with other expectations when the message is only one correct outcome; see
[waiting for whichever outcome comes first](../../../../workflows/testing/assertions/index.md#wait-for-whichever-outcome-comes-first).

## Check what the chat still shows

The history keeps every message the chat showed. `chatLines()` models what the chat still holds of them, so you
can check what the player could still find by scrolling up, such as whether a plugin's clear-chat command pushed
earlier messages out:

```java
MessageWatch chat = messages.watch();
messages.command("clearchat");
chat.received("Chat cleared");
assertFalse(messages.chatLines().contains("secret"));
```

Import `me.whereareiam.anvil.capability.messages.model.ChatLines` to use its constants. `chatLines()` returns a
snapshot without waiting: `getLines()` lists the lines oldest first, and `contains(text)` tells whether any line
contains the text. It follows the client's rules, which are the same on every supported version:

| The client | The model |
|---|---|
| Adds system messages, player chat and disguised chat | Adds the same, and never action-bar text or player chat the server's text filter hides completely |
| Adds a message only while it has a player: from the server's login until a proxy returns it to configuration | Does the same, so a message that arrives before the login, such as while the client configures, is in the history but not in the chat |
| Splits each message at its line breaks, then wraps each part at the chat's width | Splits at line breaks, so `"a\n"` is two lines and an empty message is one blank line; it cannot wrap |
| Keeps the newest 100 lines (`ChatLines.MAX_LINES`), rebuilt from the newest 100 messages | Keeps the same |
| Replaces a message the server deletes (Minecraft 1.19.1 and later) with `chat.deleted_marker` once it is three seconds old | Replaces it at once, with the marker's translation key as its text |
| Starts a new connection with an empty chat and keeps it when a proxy returns the player to configuration | Does the same |

Because wrapping only adds lines, the model never keeps fewer old lines than a real client:

- A line missing from `chatLines()` is gone from every vanilla client's chat, however wide. A server that sends
  100 blank lines leaves nothing older to find.
- A line `chatLines()` still holds is in the chat of a client wide enough not to wrap the lines after it. A narrow
  chat or a large chat scale may already have pushed it out, so the model cannot prove that a line is still
  visible.

The model assumes a client with default chat settings that shows every message it receives. The client also shows
a validation error instead of a player message it cannot verify, which a server with working chat signing never
causes. MCProtocolLib cannot read which characters a partially filtered player message hides, so such a message
counts with its whole text.

## Inspect failures

`history()` returns an immutable snapshot of what the chat showed, without waiting. Include it in a failed
assertion to see whether the command was rejected, the plugin returned another response, or no response
was shown.

When a wait fails, its message also names the latest ten messages the chat showed, since the previous wait of a
watch, which shows a refusal such as `chat.disabled.options` or another reply the server sent instead. It names the
matching messages that reached the client but that the chat left out too, with the reason: the client had no
player yet, as for a message a proxy sends while the client still configures, or the server's filter hid it. So a
failure tells a message the server never sent from one the player could not see:

```text
Player did not see a matching message in chat
The chat showed: 'Welcome', 'Please wait'
The chat left out matching messages that reached the client: 'Please register' (the client had no player)
```

Check that the player is connected and has the application's required permissions before
increasing the timeout.
