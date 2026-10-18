---
title: Messages
description: Send player chat or commands, await a matching message in captured history, and check what the chat still shows.
---

`Messages` sends chat and commands as the connected player and captures received plain text. Install
the messages unit and its Session dependency, or explicitly select the default capability unit.

## Send and await a response

This fragment belongs in a live test after Alice has connected and joined `server`. It uses
`ScenarioContext anvil`, `SimulatedPlayer alice`, and these imports:

```java
import me.whereareiam.anvil.capability.messages.Messages;
import java.time.Duration;
```

Send a message through the server console and wait for the player to receive it:

```java
Messages messages = alice.capability(Messages.class);
anvil.processes().server("server").console().sendCommand("say journey-ready-1");
String response = messages.received("journey-ready-1", Duration.ofSeconds(10));
```

For your own plugin, send its command with `messages.command("your-command arguments")` and wait for
the reply it documents. Commands accept either a leading slash or no slash. Public chat uses
`messages.chat("text")`; it sends a chat message, not a console command. Before Minecraft 1.19 the
protocol has no command packet, so the server runs a `chat` text that starts with `/` as a command;
use `command(...)` whenever you mean a command, so the step behaves the same on every version.

## Read received text

Each received chat message becomes one plain-text history entry: player chat, system messages and, on
Minecraft 1.19.4 and later, disguised chat such as `say` from the console. Action-bar text that the
server sends as chat is included too: the game-info chat type before Minecraft 1.19 and overlay system
chat from 1.19. Titles and text sent through the separate title or action-bar packets are not recorded;
[Screen](../screen/index.md) records them.

Plain text joins every part of the message in order, because the client's language file is not
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
would add.

## Match the intended operation

`received(text)` matches messages containing the supplied text. It searches captured history, so a
message received before the current operation can satisfy the wait. The bundled implementation keeps
that history across reconnects of the same player.

When an action repeats, [watch the messages](#watch-what-arrives-next) so that an earlier response
cannot satisfy the wait. For console assertions that must ignore earlier output, use a
[console watch](../../../../workflows/testing/assertions/index.md).

## Watch what arrives next

When a text repeats, such as a prompt shown again after a reconnect, start a watch before the action. A watch
sees only the messages received after it was created, and each wait continues after the message the previous
one found:

```java
MessageWatch chat = messages.watch();
messages.command("login secret");
String welcome = chat.received("Welcome back", Duration.ofSeconds(10));
```

`received` also takes any predicate on the message's plain text, so one wait can require several texts in one
message or accept alternatives: `chat.received(text -> text.contains("Welcome") && text.contains("Alice"), timeout)`.
Import `me.whereareiam.anvil.capability.messages.MessageWatch`. `unread()` lists the messages received since
the previous wait without moving the watch. Several watches on one player are independent.

## Expect that a message does not arrive

`neverReceived(...)` watches for a duration and fails as soon as a matching message has arrived since the
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

The history keeps everything that arrived. `chatLines()` instead models the chat a vanilla client keeps, so you
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

`history()` returns an immutable snapshot without waiting. Include relevant received text in a failed
assertion to see whether the command was rejected, the plugin returned another response, or no response
arrived. Check that the player is connected and has the application's required permissions before
increasing the timeout.
