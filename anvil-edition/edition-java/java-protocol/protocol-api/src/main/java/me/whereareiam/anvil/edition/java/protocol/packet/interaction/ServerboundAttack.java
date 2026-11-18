package me.whereareiam.anvil.edition.java.protocol.packet.interaction;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The player attacks an entity. Minecraft 26.1 sends an attack in a packet of its own; the player's library sends it
 * as the attack action of the entity interaction packet to earlier releases.
 */
@Value
public class ServerboundAttack implements ServerboundPacket {
	/**
	 * The protocol ID of the attacked entity.
	 */
	@NotNull EntityId entityId;
}
