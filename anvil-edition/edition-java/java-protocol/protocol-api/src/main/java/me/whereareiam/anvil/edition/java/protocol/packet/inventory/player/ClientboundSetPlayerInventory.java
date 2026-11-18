package me.whereareiam.anvil.edition.java.protocol.packet.inventory.player;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.PlayerInventoryLayout;
import me.whereareiam.anvil.edition.java.protocol.model.inventory.ItemStack;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.Nullable;

/**
 * The new content of one slot of the player's own inventory, addressed by its {@link PlayerInventoryLayout inventory index}
 * instead of a container slot. Releases before Minecraft 1.21.2 address the player's inventory through the slot
 * packet of a container of their own, which the player's library reports as this packet too.
 */
@Value
@FieldNameConstants
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
