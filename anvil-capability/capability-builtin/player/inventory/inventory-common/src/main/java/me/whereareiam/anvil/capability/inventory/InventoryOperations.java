package me.whereareiam.anvil.capability.inventory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;

/**
 * Typed inventory operations and events shared by the host provider and the worker's inventory binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InventoryOperations {
	/**
	 * Selects the held hotbar slot.
	 */
	public static final ChannelOperation<SlotSelection, Void> SELECT = new ChannelOperation<>("inventory.select", SlotSelection.class, Void.class);
	/**
	 * Clicks a slot of the latest received container.
	 */
	public static final ChannelOperation<InventoryAction, Void> CLICK = new ChannelOperation<>("inventory.click", InventoryAction.class, Void.class);
	/**
	 * Complete observed inventory contents for one player.
	 */
	public static final EventDescriptor<InventorySnapshot> CHANGED = new EventDescriptor<>("inventory.changed", InventorySnapshot.class);
}
