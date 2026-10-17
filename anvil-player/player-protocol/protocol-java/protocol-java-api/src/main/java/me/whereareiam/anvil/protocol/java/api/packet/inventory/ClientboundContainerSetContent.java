package me.whereareiam.anvil.protocol.java.api.packet.inventory;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The complete contents of a container, which replace everything the player knew about it.
 */
@Value
public class ClientboundContainerSetContent implements ClientboundPacket {
	/**
	 * ID of the container, {@code 0} for the player's own inventory.
	 */
	int containerId;
	/**
	 * State ID of the contents, which the player's next click on the container carries.
	 */
	int stateId;
	/**
	 * Every slot of the container in slot order, with {@code null} for each empty slot.
	 */
	@NotNull List<@Nullable ItemStack> items;
	/**
	 * The stack the player carries on the cursor, or {@code null} for none.
	 */
	@Nullable ItemStack carriedItem;
}
