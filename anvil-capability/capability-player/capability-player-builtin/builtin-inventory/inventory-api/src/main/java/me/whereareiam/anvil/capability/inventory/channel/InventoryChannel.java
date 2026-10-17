package me.whereareiam.anvil.capability.inventory.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host inventory capability and its worker extensions: the capability ID both
 * register under, the operations the host requests and the event the worker emits.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InventoryChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.inventory";
	/**
	 * Selects the held hotbar slot.
	 */
	public static final OperationDescriptor<SlotSelection, Void> SELECT = new OperationDescriptor<>(
			"inventory.select", SlotSelection.class, Void.class
	);
	/**
	 * Clicks a slot of the latest received container.
	 */
	public static final OperationDescriptor<InventoryAction, Void> CLICK = new OperationDescriptor<>(
			"inventory.click", InventoryAction.class, Void.class
	);
	/**
	 * Complete observed inventory contents for one player.
	 */
	public static final EventDescriptor<InventorySnapshot> CHANGED = new EventDescriptor<>(
			"inventory.changed", InventorySnapshot.class
	);
}
