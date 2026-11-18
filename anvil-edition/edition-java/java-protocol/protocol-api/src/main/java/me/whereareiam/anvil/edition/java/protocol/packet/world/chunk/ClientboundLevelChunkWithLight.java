package me.whereareiam.anvil.edition.java.protocol.packet.world.chunk;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server sends the player a whole chunk column, which replaces what the client had of it. The protocol carries
 * only the column's block states; its light, height maps, biomes and block entities are left out. A release decodes
 * the column only while something wants it, such as the player's {@code JavaWorld} or a listener of this packet.
 */
@Value
@FieldNameConstants
public class ClientboundLevelChunkWithLight implements ClientboundPacket {
	/**
	 * The column's position.
	 */
	@NotNull ChunkPosition position;
	/**
	 * The column's sections from the lowest section of the player's level up. Releases from Minecraft 1.18 send every
	 * section of the level; earlier ones leave out sections of only air, which appear here as
	 * {@link LevelChunkSection#EMPTY}, and Minecraft 1.17 may list sections above the level's height, which hold only
	 * air too.
	 */
	@NotNull List<LevelChunkSection> sections;
}
