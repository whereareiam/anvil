package me.whereareiam.anvil.protocol.java.api.packet.interaction;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;

/**
 * The player attacks an entity. Minecraft 26.1 sends an attack in a packet of its own; the player's library sends it
 * as the attack action of the entity interaction packet to earlier releases.
 */
@Value
public class ServerboundAttack implements ServerboundPacket {
	/**
	 * The protocol ID of the attacked entity.
	 */
	int entityId;
}
