package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server sends the player a whole chunk column, which replaces what the client had of it. The protocol models
 * only the column's block states; its light, height maps, biomes and block entities are left out.
 */
@Value
public class ClientboundLevelChunkWithLight implements ClientboundPacket {
	/**
	 * The column's position.
	 */
	@NotNull ChunkPosition position;
	/**
	 * The column's sections from the lowest section of the player's dimension up. Releases from Minecraft 1.18 send
	 * every section of the dimension; earlier ones leave out sections of only air, which appear here as
	 * {@link LevelChunkSection#EMPTY}, and may list sections above the dimension's height, which hold only air too.
	 */
	@NotNull List<LevelChunkSection> sections;
}
