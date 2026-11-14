package me.whereareiam.anvil.protocol.java.api.packet.health;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.ClientCommandAction;
import org.jetbrains.annotations.NotNull;

/**
 * Asks the server to respawn the dead player, as the death screen's respawn button does, or to send the player's
 * statistics.
 */
@Value
public class ServerboundClientCommand implements ServerboundPacket {
	/**
	 * What the client asks for.
	 */
	@NotNull ClientCommandAction action;
}
