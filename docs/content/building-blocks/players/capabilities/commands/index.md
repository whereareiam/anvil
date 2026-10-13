---
title: Commands
description: Read the command tree a player received and check commands against it as the client does.
---

`Commands` holds the command tree the server sends a player, which tells the client which commands
the player may run, and checks commands against it as the client does. It works on every Java Edition
release Anvil ships. Bedrock players, which connect through CloudburstMC Protocol, do not get it:
retrieving it fails with `CapabilityUnavailableException`, whose message says that it is provided only for
the `mcprotocol` library. Install the commands unit and its Session dependency, or explicitly select the
default capability unit.

## Wait for a command

A plugin often sends the commands a player may use only after something happens, such as a login.
Wait for a tree that has the command, from a live test after Alice has connected:

```java
import me.whereareiam.anvil.capability.commands.Commands;
import java.time.Duration;

Commands commands = alice.capability(Commands.class);
commands.tree(tree -> tree.literal("spawn").isPresent(), Duration.ofSeconds(10));
```

`tree()` returns the tree the player holds now without waiting. The tree is the last one the player's
current connection received; after a reconnect it is empty until the server sends one again.
`CommandTree.literal(name)` finds a command by its name, and `getNodes()` gives every node with its
children, redirect, parser and whether it needs permissions.

## Check a command as the client does

From Minecraft 1.21.6 the client checks a command it runs on its own, for a dialog button or a
clicked message, before it sends it. `check(command)` does the same against the player's tree:

| Result | The command |
|---|---|
| `NO_ISSUES` | Parses and uses nothing that needs permissions; the client sends it |
| `PARSE_ERRORS` | Does not parse, or the player holds no tree; the client asks whether to run it |
| `PERMISSIONS_REQUIRED` | Uses a part the server marked as needing permissions; the client asks |
| `SIGNATURE_REQUIRED` | Has a signed argument, such as the message of `/msg`; the client never sends it |

```java
assertEquals(CommandCheck.NO_ISSUES, commands.check("login Secret123"));
```

Import `me.whereareiam.anvil.capability.commands.type.CommandCheck`. Releases before 1.21.6 do not mark
nodes that need permissions, so there the check never finds `PERMISSIONS_REQUIRED`.

Anvil parses commands as Brigadier does: literals, the Brigadier argument types and the message argument
exactly, coordinates as their parts, entity selectors with their options, and any other argument as one
word, in which quotes and brackets may hold spaces, without checking that its text is valid for its type.
A plugin command with a Brigadier string argument is therefore checked as the client checks it: a
single-word argument does not accept `!`, for example.
