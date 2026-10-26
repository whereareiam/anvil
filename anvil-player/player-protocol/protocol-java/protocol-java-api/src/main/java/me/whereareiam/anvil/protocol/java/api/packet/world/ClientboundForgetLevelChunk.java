package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server tells the player to forget a chunk column, usually one that left its view distance.
 */
@Value
public class ClientboundForgetLevelChunk implements ClientboundPacket {
	/**
	 * The column's position.
	 */
	@NotNull ChunkPosition position;
}
