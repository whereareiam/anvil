---
title: Inventory
description: Find and click the items of plugin menus by name, lore and type, follow the screens that open, and read inventory state.
---

`Inventory` shows what the player's client sees of its inventory and of the containers the server opens, such as
the chest menus plugins build, and clicks their slots. Install the inventory unit and Session dependency, or
explicitly select the default capability unit.

## Click through a plugin menu

Plugin menus are chests whose items carry names and lore. A test finds those items by what the player reads, clicks
them, and waits for the screen that follows, without counting slots.

This fragment uses a connected `SimulatedPlayer alice` on a server whose plugin opens a chest titled `Shop` on
`/shop`. Clicking its `Buy` item opens a chest titled `Confirm purchase`, whose `Yes` item completes the purchase and
closes the menu. Import:

```java
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.InventoryWatch;
import me.whereareiam.anvil.capability.inventory.Screen;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.messages.Messages;
```

Then start a watch before the command that opens the menu, and follow it screen by screen:

```java
Inventory inventory = alice.capability(Inventory.class);
Messages messages = alice.capability(Messages.class);
InventoryWatch watch = inventory.watch();

messages.command("shop");
Screen shop = watch.screen("Shop");
shop.items().type("minecraft:emerald").named("Buy").loreContaining("64 coins").click(InventoryClick.LEFT);

Screen confirm = watch.screen("Confirm purchase");
confirm.items().named("Yes").click(InventoryClick.LEFT);
watch.closed();
```

The test passes when the shop opened, exactly one emerald named `Buy` with that lore was clicked, the confirmation
opened after it, and the server closed the menu after `Yes`. Then assert what the purchase changed, such as the
plugin's message or the player's items.

A watch finds only screens that open after it started, so create it before the action that opens the menu. Each wait
moves the watch past the screen it found: `watch.screen("Shop")` after the purchase waits for the shop to open again.
Several watches of one player follow its screens independently.

## Find items

`inventory.items()` locates items on the screen the player sees whenever it acts: the open container, including the
player's inventory slots below it, or the player's own inventory. `screen.items()` locates items of one screen a watch
found. Each condition returns a new locator:

| Condition | Matches an item |
| --- | --- |
| `type("minecraft:emerald")` | of that type, by its registry key |
| `named("Buy")` | whose own name reads exactly `Buy` |
| `nameContaining("Buy")` | whose own name contains `Buy` |
| `named("green", name -> ...)` | whose own name, as the server sent it, satisfies the condition |
| `lore("Click to buy")` | with a line of lore that reads exactly so |
| `loreContaining("64 coins")` | with a line of lore that contains the text |
| `lore("description", line -> ...)` | with a line of lore that satisfies the condition |
| `matching("description", item -> ...)` | that satisfies the condition, such as `item.getSlot() < 27` |

Names and lore are compared by the plain text the player reads. They are `Text` values, the text the server sent,
so a condition can check colors and styles as well, as [Messages](../messages/index.md) does for chat:

```java
InventoryItem buy = shop.items().named("Buy").item();
assertEquals(TextColor.GREEN, buy.getDisplay().getName().span("Buy").orElseThrow().getStyle().getColor());
```

An item's own name is its custom name, else its item name, which Minecraft 1.20.5 introduced;
`getDisplay().getNameSource()` says which, `CUSTOM` or `ITEM`. An item without either is shown by the game's
translated name of its type, which a simulated player does not have, so find it by `type(...)` or `matching(...)`.

A locator is strict. `item()` and `click(...)` wait, ten seconds by default, until exactly one item matches. When
several match, they fail and name their slots; narrow the locator or pick one with `first()` or `nth(index)`, which
count matches in slot order. `all()` and `count()` read the latest state without waiting.

A locator never stores a slot: each action matches again against the screen's latest state, so one locator can be
reused while the screen changes. A screen's locator refuses to act once another screen replaced it, and fails with
`the screen is no longer open` instead of clicking into the wrong menu. `screen.open()` tells whether it is still
shown.

`click(InventoryClick.HOTBAR_SWAP, 2)` swaps the located item with hotbar slot `2`. Other modes take no button.

### When a locator fails

| Message | Next check |
| --- | --- |
| `Player did not find exactly one item named 'Buy' ...` | Read `inventory.snapshot().getItems()` to see the names and lore the client received. Formatting codes and spaces count. |
| `Player found 2 items ... in the slots 11, 15` | Add a condition, or pick one with `first()` or `nth(index)`. |
| `... the screen is no longer open` | The server opened another screen or closed the menu before the action. Wait for the new screen through the watch. |
| `Cannot find an item of type ...: the player's items have no type` | Anvil ships no item data for the player's protocol. Locate the item by name, lore or `matching(...)`. |
| `Player did not see a screen titled 'Shop'` | Check that the watch started before the action, and compare the title with `inventory.snapshot().getTitle()`. |

## Wait for screens

`InventoryWatch` waits for the screens the player sees:

- `screen("Shop")` waits for a container whose title reads exactly so.
- `screen("with a Buy button", state -> ...)` waits for a container whose latest state satisfies the condition, such
  as holding an item.
- `closed()` waits until the player's own inventory is shown again because the server closed the container.

Each waits ten seconds by default and has an overload taking a `Duration`. A screen is reported once the server
filled it, so its items are present when the wait returns. `Screen` gives the container's `title()`, `menuType()`,
such as `minecraft:generic_9x3`, and `snapshot()`, its latest state.

## Read inventory state

`snapshot()` reads the latest state immediately. `matching(predicate)` waits for a state that satisfies a condition;
it can return at once when the current state already does, so write a condition that only the intended result meets.

An `InventorySnapshot` carries:

- `getContainerId()`: `0` for the player's own inventory, else the open container's ID.
- `getScreen()`: the screen's number. Each container the server opens, and each return to the player's inventory, is
  a new screen; later changes of one screen keep its number.
- `getTitle()` and `getMenuType()`: the container's title and menu type, `null` for the player's inventory.
- `getStateId()`: the state ID the next click carries.
- `getItems()`: one `InventoryItem` per filled slot:
  - `getSlot()` and `getAmount()`;
  - `getType()`: its `getIdentifier()`, the registry key such as `minecraft:emerald`, and `getProtocolId()`, the
    numeric ID, which differs between Minecraft versions;
  - `getDisplay()`: the text the player reads, its `getName()`, `getNameSource()` and `getLore()`.

The player keeps its screens as the vanilla client does. A container the server opens is the screen until the server
closes it or opens another; contents and slot changes of other containers and of the cursor are ignored. An opened
container shows the player's main inventory and hotbar as its last slots, so a change of either, addressed to the
container or to the player's inventory, shows on both, and the player's inventory is current again when the
container closes.

## Select a slot or click by index

`inventory.selectSlot(0)` selects the first hotbar slot; valid values are `0..8`. `inventory.click(slot, click,
button)` clicks a slot of the current screen by its index:

```java
inventory.click(4, InventoryClick.LEFT, 0);
```

Click modes include `LEFT`, `RIGHT`, `SHIFT_LEFT`, `HOTBAR_SWAP`, `DROP_ONE`, and `DROP_STACK`. For
`HOTBAR_SWAP`, the third argument is the hotbar slot `0` to `8`, or `40` to swap with the off hand; use zero
for the other modes. A click carries the latest received container state, so wait for the intended screen before
acting. Crafting automation is outside this capability's scope.

## Behavior across Minecraft versions

Inventory works on every Minecraft version the MCProtocol library supports, with these differences:

- Items have item names from Minecraft 1.20.5 on. Before, an item's own name is always its custom name.
- Item types come from the item data Anvil ships for each protocol, generated from PrismarineJS minecraft-data.
  minecraft-data has none for Minecraft 26.2 and 26.3 yet, so their items carry no type and a locator with
  `type(...)` fails there, saying so.
- MCProtocolLib cannot send the off-hand swap for Minecraft 1.16.5 and 1.17.1.
