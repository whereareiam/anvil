package me.whereareiam.anvil.protocol.java.api.packet.inventory;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;

/**
 * The hotbar slot the player holds.
 */
@Value
public class ServerboundSetCarriedItem implements ServerboundPacket {
	/**
	 * The hotbar slot, from zero through eight.
	 */
	int slot;
}
