package me.whereareiam.anvil.protocol.java.api.packet.inventory;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.PlayerInventoryLayout;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.Nullable;

/**
 * The new content of one slot of the player's own inventory, addressed by its {@link PlayerInventoryLayout inventory index}
 * instead of a container slot. Releases before Minecraft 1.21.2 address the player's inventory through the slot
 * packet of a container of their own, which the player's library reports as this packet too.
 */
@Value
public class ClientboundSetPlayerInventory implements ClientboundPacket {
	/**
	 * The inventory index, as {@link PlayerInventoryLayout} lays them out.
	 */
	int slot;
	/**
	 * The slot's new stack, or {@code null} when it is empty.
	 */
	@Nullable ItemStack item;
}
