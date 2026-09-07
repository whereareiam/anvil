---
title: Inventory
description: Wait for inventory or container state and send selection and click actions.
---

`Inventory` observes the latest client inventory or open container and can select a hotbar slot or
click a container slot. Install the inventory unit and Session dependency, or use the umbrella plugin.

## Wait for a supplied item

This fragment uses `ScenarioContext anvil` and a connected `SimulatedPlayer alice` on the first-test
Paper server. Start with a fresh player whose inventory is empty. Import:

```java
import me.whereareiam.anvil.capability.inventory.Inventory;
import java.time.Duration;
```

Then give an item and wait for an inventory update:

```java
Inventory inventory = alice.capability(Inventory.class);
anvil.processes().server("server").console().sendCommand("give Alice minecraft:stone 1");
var snapshot = inventory.matching(
		state -> state.getItems().stream().anyMatch(item -> item.getAmount() > 0),
		Duration.ofSeconds(10)
);
```

This proves the previously empty inventory received an item. It does not identify that item as stone.
Each `InventoryItem` carries a slot, protocol item ID, amount, and optional textual identifier. The
bundled MCProtocol adapter supplies protocol IDs and amounts; its identifier is currently absent.
Do not assume a numeric protocol ID is stable across Minecraft versions.

## Select a slot or click a menu

`inventory.selectSlot(0)` selects the first hotbar slot; valid values are `0..8`.
For a plugin that opens a known menu, first wait for its expected slot content, then click that slot.
This fragment assumes `int expectedMenuItemProtocolId` comes from your fixture for the selected native
version and identifies the item the menu places in slot `4`:

```java
inventory.matching(state -> state.getContainerId() != 0
		&& state.getItems().stream().anyMatch(item -> item.getSlot() == 4
				&& item.getProtocolId() == expectedMenuItemProtocolId
				&& item.getAmount() > 0), Duration.ofSeconds(10));
inventory.click(4, InventoryClick.LEFT, 0);
```

Import `me.whereareiam.anvil.capability.inventory.type.InventoryClick`. The menu must already have been
opened by your plugin's command or interaction, and slot `4` must be the action your test intends to
exercise. Then wait for the plugin's result, such as a message or changed inventory.

An open-screen packet can produce a nonzero container ID before its content arrives. Waiting for
the expected item prevents a click against that initial empty snapshot.

Click modes include `LEFT`, `RIGHT`, `SHIFT_LEFT`, `HOTBAR_SWAP`, `DROP_ONE`, and `DROP_STACK`. The third
argument specifies a hotbar button for `HOTBAR_SWAP`; use zero for the other modes.

## Interpret snapshots

`snapshot()` reads immediately. `matching(predicate, timeout)` waits for a matching state. Both describe
client-observed data, not a full server inventory API. The adapter uses its latest received container
state when sending a click, so wait for the intended container before acting. Crafting automation is
outside this capability's scope.
