package me.whereareiam.anvil.edition.java.protocol.packet.world.chunk;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server tells the player to forget a chunk column, usually one that left its view distance.
 */
@Value
@FieldNameConstants
public class ClientboundForgetLevelChunk implements ClientboundPacket {
	/**
	 * The column's position.
	 */
	@NotNull ChunkPosition position;
}
