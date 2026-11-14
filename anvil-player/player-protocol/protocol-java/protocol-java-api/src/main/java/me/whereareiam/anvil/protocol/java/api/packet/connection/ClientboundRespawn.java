package me.whereareiam.anvil.protocol.java.api.packet.connection;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server respawns the player or moves it to another dimension, and the client replaces its screens with the new
 * level. The protocol models only the dimension the player is in afterwards of the packet's fields.
 */
@Value
public class ClientboundRespawn implements ClientboundPacket {
	/**
	 * The key of the level the player is in after the respawn, with its namespace, such as {@code minecraft:the_nether};
	 * the same key as before when the player respawns in the level it was in.
	 */
	@NotNull String dimension;
}
