package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Inventory capability driven over a player's typed channel: selections and clicks go to the worker's inventory
 * binding, and the snapshots it publishes keep the latest observed state. The channel releases the event
 * subscription together with the player.
 */
final class ChannelInventory implements Inventory {
	private final CapabilityChannel channel;
	private final AtomicReference<InventorySnapshot> snapshot = new AtomicReference<>(
			InventorySnapshot.builder().containerId(0).stateId(0).build()
	);

	ChannelInventory(@NotNull CapabilityChannel channel) {
		this.channel = channel;
		channel.subscribe(InventoryOperations.CHANGED, snapshot::set);
	}

	@Override
	public @NotNull InventorySnapshot snapshot() {
		return snapshot.get();
	}

	@Override
	public @NotNull InventorySnapshot matching(
			@NotNull Predicate<InventorySnapshot> predicate,
			@NotNull Duration timeout
	) {
		channel.await(() -> predicate.test(snapshot.get()), "receive matching inventory", timeout);
		return snapshot.get();
	}

	@Override
	public void selectSlot(int slot) {
		if (slot < 0 || slot > 8)
			throw new IllegalArgumentException("Hotbar slot must be in range 0..8");

		channel.request(InventoryOperations.SELECT, new SlotSelection(slot));
	}

	@Override
	public void click(int slot, @NotNull InventoryClick click, int button) {
		channel.request(InventoryOperations.CLICK, new InventoryAction(slot, click, button));
	}
}
