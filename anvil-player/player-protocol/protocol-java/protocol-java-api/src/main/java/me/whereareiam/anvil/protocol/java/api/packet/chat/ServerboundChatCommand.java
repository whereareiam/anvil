package me.whereareiam.anvil.protocol.java.api.packet.chat;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A command the player runs.
 */
@Value
public class ServerboundChatCommand implements ServerboundPacket {
	/**
	 * The command without its leading slash.
	 */
	@NotNull String command;
}
