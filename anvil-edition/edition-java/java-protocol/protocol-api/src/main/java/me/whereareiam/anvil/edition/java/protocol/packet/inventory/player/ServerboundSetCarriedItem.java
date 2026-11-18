package me.whereareiam.anvil.edition.java.protocol.packet.inventory.player;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;

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
