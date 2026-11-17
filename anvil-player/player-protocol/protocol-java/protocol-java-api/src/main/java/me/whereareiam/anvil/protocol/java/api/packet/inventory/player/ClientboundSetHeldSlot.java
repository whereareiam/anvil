package me.whereareiam.anvil.protocol.java.api.packet.inventory.player;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server selects the hotbar slot the player holds. A vanilla client selects a slot of the hotbar and ignores any
 * other, and tells the server the slot it then holds as it tells every change of its selection.
 */
@Value
@FieldNameConstants
public class ClientboundSetHeldSlot implements ClientboundPacket {
	/**
	 * The slot the server selects, which is a hotbar slot from zero through eight unless the server sends another.
	 */
	int slot;
}
