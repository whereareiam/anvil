---
title: Messages
description: Send player chat or commands and await a matching message in captured history.
---

`Messages` sends chat and commands as the connected player and captures received plain text. Install
the messages unit and its Session dependency, or use the umbrella plugin.

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
`messages.chat("text")`; it sends a chat message, not a console command.

## Match the intended operation

`received(text)` matches messages containing the supplied text. It searches captured history, so a
message received before the current operation can satisfy the wait. The bundled implementation keeps
that history across reconnects of the same player.

Use a response token unique to the step or an application state observation when repeating an action.
For example, have a test command echo a request identifier. The Messages API does not expose a history
clear operation or a checkpoint parameter. For console assertions that must ignore earlier output,
use a [console checkpoint](../../../../workflows/testing/assertions/index.md).

## Inspect failures

`history()` returns an immutable snapshot without waiting. Include relevant received text in a failed
assertion to see whether the command was rejected, the plugin returned another response, or no response
arrived. Check that the player is connected and has the application's required permissions before
increasing the timeout.
