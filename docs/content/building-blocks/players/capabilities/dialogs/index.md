---
title: Dialogs
description: Wait for a dialog the server shows, fill its inputs and press its buttons as the client does.
---

`Dialogs` observes the dialogs the server shows a player and presses their buttons as the Minecraft
client does. Dialogs arrived in Minecraft 1.21.6, so players on 1.21.11 and 26.1.2 get this capability;
players on older releases do not, and retrieving it fails with `CapabilityUnavailableException`. Dialogs
are Java Edition behaviour: Bedrock players, which connect through CloudburstMC Protocol, do not get the
capability either, and the exception's message says that it is provided only for the `mcprotocol` library.
Install the dialogs unit and its Session dependency, or explicitly select the default capability unit.

## Answer a dialog

This fragment belongs in a live test after Alice has connected and joined a server whose plugin shows
her a login dialog. It uses `SimulatedPlayer alice` and these imports:

```java
import me.whereareiam.anvil.capability.dialogs.Dialogs;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import java.time.Duration;
import java.util.Map;
```

Wait for the dialog by its title, fill its password input and press its button:

```java
Dialogs dialogs = alice.capability(Dialogs.class);
Dialog login = dialogs.shown("Log in", Duration.ofSeconds(10));
dialogs.press("Log in", Map.of("password", "Secret123!"));
dialogs.closed(Duration.ofSeconds(10));
```

`shown(title)` waits until the open dialog's title contains the text. `press(button, inputs)` takes the
button's label, matched exactly, and the values of the inputs to fill by their keys; inputs left out keep
their initial value. `press(button)` presses with every input at its initial value.

## What a button does

Anvil does what the Minecraft client does for the button's action:

| Action | The client |
|---|---|
| `run_command` | Runs the command, without its leading slash, after [checking it](#commands-the-client-asks-about) |
| `dynamic/run_command` | Runs the template with each `$(key)` replaced by the value of that input, after checking it |
| `custom` | Sends a custom click with the action's ID and payload |
| `dynamic/custom` | Sends a custom click with the action's ID; its payload holds the action's additions and every input value under its key |
| `show_dialog` | Opens the dialog in place of this one |
| `suggest_command`, `open_url`, `copy_to_clipboard`, `change_page`, or no action | Sends nothing |

A template puts in the text of a text input, the ID of the selected option, the number of a slider,
without a fraction when it is whole, and the `on_true` or `on_false` text of a checkbox. A custom payload
holds text and options as strings, a checkbox as a byte and a slider as a float. A value the input would
not accept fails the press: text longer than its maximum length, a checkbox value other than `true` or
`false`, an option the input does not have, or a number outside the slider's range.

After the action the dialog stays open, closes or gives way to the waiting screen, as its `after_action`
says. `waitingForResponse()` tells whether the waiting screen is shown; it stays until the server shows
another dialog, and `close()` leaves it once the client enables its back button, five seconds after it
appeared.

## Commands the client asks about

Before it runs a command from a button, the client checks it against the command tree the server sent,
as [Commands](../commands/index.md#check-a-command-as-the-client-does) describes:

| The check finds | The client |
|---|---|
| `NO_ISSUES` | Sends the command |
| `PARSE_ERRORS` or `PERMISSIONS_REQUIRED` | Asks whether to run it |
| `SIGNATURE_REQUIRED` | Asks, and never sends it; it only offers to put it into the chat input or the clipboard |

While the client asks, `confirmation()` returns the command and the reason, and `current()` is empty.
`confirm()` runs the command, or sends nothing for a signed one, and `decline()` returns to the dialog:

```java
dialogs.press("Teleport");
CommandConfirmation question = dialogs.confirmation().orElseThrow();
assertEquals(CommandCheck.PERMISSIONS_REQUIRED, question.getReason());
dialogs.confirm();
```

Import `me.whereareiam.anvil.capability.dialogs.model.click.CommandConfirmation` and
`me.whereareiam.anvil.capability.commands.type.CommandCheck`.

The check needs the player's `Commands` capability, which the default capability unit includes. Dialogs
work without it, except that pressing a button that runs a command then fails and asks to install
it.

## Follow the screens

`current()` returns the dialog on screen, and `history()` every dialog the player was shown, including
dialogs a button opened, across reconnects. Like the client:

- a dialog the server shows replaces the open dialog and returns to the screen below it;
- the server's clear packet returns from the open dialog to the screen below it;
- joining a level, respawning, changing servers behind a proxy, starting configuration again or
  reconnecting closes every dialog;
- `close()` presses Escape: on a dialog it presses the exit button, if there is one, and closes the
  dialog, which fails for a dialog that cannot be closed with Escape; on a confirmation it declines.

A `Dialog` holds its type, such as `minecraft:multi_action`, its title, its body texts, its inputs and its
buttons, the exit button last. A dialog list has a button for each dialog it lists, labelled with the
dialog's external title; a dialog tag lists nothing. Its text is plain text, built as
[Messages](../messages/index.md#read-received-text) builds received messages, so a default notice button
reads `gui.ok`.

A dialog shown during the configuration phase has `isConfiguration()` set. The client runs no commands
there: a button that runs one sends nothing and leaves the dialog as it is.

The server may refer to a dialog of its dialog registry, such as one from a data pack, instead of sending
it. Anvil resolves it from the registry the server sent during configuration and keeps its
`getRegistryId()` and `getRegistryName()`. A dialog the server left out of the registry because the
client knows it, such as a built-in one, carries only those two; its title, body, inputs and buttons are
empty.

## Wait from a checkpoint

When the same dialog is shown again, such as a login form after a wrong password, wait from a
checkpoint so the earlier dialog cannot satisfy the wait:

```java
int before = dialogs.checkpoint();
dialogs.press("Log in", Map.of("password", "wrong"));
ShownDialog again = dialogs.shown(dialog -> dialog.getBody().contains("Invalid password."), before, Duration.ofSeconds(10));
dialogs.notShown(dialog -> dialog.getTitle().contains("Welcome"), again.getCheckpoint(), Duration.ofSeconds(2));
```

The result carries the dialog and the checkpoint directly after it. Import
`me.whereareiam.anvil.capability.dialogs.model.dialog.ShownDialog`.
