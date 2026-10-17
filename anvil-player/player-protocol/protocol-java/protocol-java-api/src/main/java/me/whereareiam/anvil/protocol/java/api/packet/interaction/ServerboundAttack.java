package me.whereareiam.anvil.protocol.java.api.packet.interaction;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;

/**
 * The player attacks an entity, a packet of Minecraft 26.1 and later; earlier releases send an attack as a
 * {@link ServerboundInteract} with the attack action.
 */
@Value
public class ServerboundAttack implements ServerboundPacket {
	/**
	 * The protocol ID of the attacked entity.
	 */
	int entityId;
}
