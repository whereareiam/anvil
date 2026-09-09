package me.whereareiam.anvil.capability.inventory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;

/**
 * Typed inventory operations implemented by native capability bindings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InventoryOperations {
	/**
	 * Executes the select operation with a registered request schema.
	 */
	public static final ChannelOperation<SlotSelection, Void> SELECT = new ChannelOperation<>("inventory.select", SlotSelection.class, Void.class);
	/**
	 * Executes the click operation with a registered request schema.
	 */
	public static final ChannelOperation<InventoryAction, Void> CLICK = new ChannelOperation<>("inventory.click", InventoryAction.class, Void.class);
	/**
	 * Complete observed inventory contents for one player.
	 */
	public static final EventDescriptor<InventorySnapshot> CHANGED = new EventDescriptor<>("inventory.changed", InventorySnapshot.class);
}
