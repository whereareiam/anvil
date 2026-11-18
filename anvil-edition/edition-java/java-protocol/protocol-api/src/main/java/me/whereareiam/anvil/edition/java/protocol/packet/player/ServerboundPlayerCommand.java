package me.whereareiam.anvil.edition.java.protocol.packet.player;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
import org.jetbrains.annotations.NotNull;

/**
 * The player starts or stops an action the server tracks, such as sprinting. The protocol models the movement
 * actions of the packet.
 */
@Value
public class ServerboundPlayerCommand implements ServerboundPacket {
	/**
	 * The protocol ID of the player's own entity, as the login names it.
	 */
	@NotNull EntityId entityId;
	/**
	 * The action the player starts or stops.
	 */
	@NotNull PlayerCommandAction action;
}
