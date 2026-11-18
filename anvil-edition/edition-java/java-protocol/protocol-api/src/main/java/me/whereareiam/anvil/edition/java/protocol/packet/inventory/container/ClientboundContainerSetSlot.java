package me.whereareiam.anvil.edition.java.protocol.packet.inventory.container;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.inventory.ItemStack;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.Nullable;

/**
 * The new content of one container slot.
 */
@Value
@FieldNameConstants
public class ClientboundContainerSetSlot implements ClientboundPacket {
	/**
	 * Container the slot belongs to, or {@code -1} for the cursor.
	 */
	int containerId;
	/**
	 * State ID of the container after the change.
	 */
	int stateId;
	/**
	 * The container slot, or {@code -1} for the cursor.
	 */
	int slot;
	/**
	 * The slot's new stack, or {@code null} when it is empty.
	 */
	@Nullable ItemStack item;
}
