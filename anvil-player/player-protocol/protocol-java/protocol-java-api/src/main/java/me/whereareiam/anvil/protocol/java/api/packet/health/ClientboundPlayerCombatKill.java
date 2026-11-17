package me.whereareiam.anvil.protocol.java.api.packet.health;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The player died: the death screen opens with the death message, unless the server turned the death screen off.
 * Releases before Minecraft 1.17 send it as the death state of their combat packet. The packet's killer, which
 * releases before Minecraft 1.20 carry, is not modelled.
 */
@Value
@FieldNameConstants
public class ClientboundPlayerCombatKill implements ClientboundPacket {
	/**
	 * The protocol ID of the player's own entity.
	 */
	@NotNull EntityId playerId;
	/**
	 * The death message, flattened to plain text; translated messages keep their key and arguments, as chat does.
	 */
	@NotNull String message;
}
