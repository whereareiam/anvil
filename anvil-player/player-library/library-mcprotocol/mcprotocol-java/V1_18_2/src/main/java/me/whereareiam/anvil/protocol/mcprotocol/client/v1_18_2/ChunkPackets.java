package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.chunk.ChunkSection;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockChangeEntry;
import com.github.steveice10.packetlib.io.NetInput;
import com.github.steveice10.packetlib.io.stream.StreamNetInput;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The chunk columns and block updates of the MCProtocolLib 1.18.2 release, which reads a section from a stream and
 * names positions with its own type.
 */
public final class ChunkPackets extends ChunkTranslation {
	/**
	 * Creates the translation of one connection.
	 *
	 * @param level the connection's level
	 * @param demand the packets the connection wants decoded
	 */
	public ChunkPackets(@NotNull ConnectionLevel level, @NotNull PacketDemand demand) {
		super(level, demand);
	}

	@Override
	protected @NotNull List<LevelChunkSection> sections(byte @NotNull [] data, int count) throws IOException {
		NetInput in = new StreamNetInput(new ByteArrayInputStream(data));
		List<LevelChunkSection> sections = new ArrayList<>(count);
		for (int section = 0; section < count; section++)
			sections.add(section(ChunkSection.read(in, UNWRITTEN_GLOBAL_BITS).getChunkData()));

		return sections;
	}

	@Override
	protected @NotNull BlockPosition position(@NotNull BlockChangeEntry entry) {
		Position position = entry.getPosition();
		return BlockPosition.builder().x(position.getX()).y(position.getY()).z(position.getZ()).build();
	}
}
