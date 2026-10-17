package me.whereareiam.anvil.protocol.java.api.packet.inventory;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.Nullable;

/**
 * The new content of one slot of the player's own inventory, addressed by inventory index instead of a container
 * slot: hotbar {@code 0..8}, main inventory {@code 9..35}, armor {@code 36..39} from feet to head and the off hand
 * {@code 40}. Releases before Minecraft 1.21.2 send a {@link ClientboundContainerSetSlot} of container {@code -2}
 * instead.
 */
@Value
public class ClientboundSetPlayerInventory implements ClientboundPacket {
	/**
	 * The inventory index.
	 */
	int slot;
	/**
	 * The slot's new stack, or {@code null} when it is empty.
	 */
	@Nullable ItemStack item;
}
