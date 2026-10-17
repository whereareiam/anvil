package me.whereareiam.anvil.protocol.java.api.packet.chat;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A public chat message the player sends.
 */
@Value
public class ServerboundChat implements ServerboundPacket {
	/**
	 * The message text.
	 */
	@NotNull String text;
}
