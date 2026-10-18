package me.whereareiam.anvil.protocol.java.api.packet.connection;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server respawns the player or moves it to another level, and the client replaces its screens with the new
 * level. The client forgets its chunks only when the level's key changes. The protocol models the level of the
 * packet's fields.
 */
@Value
public class ClientboundRespawn implements ClientboundPacket {
	/**
	 * The level the player respawns in.
	 */
	@NotNull Dimension dimension;
}
