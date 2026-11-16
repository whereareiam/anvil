package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.codec.MinecraftCodec;
import com.github.steveice10.mc.protocol.codec.MinecraftCodecHelper;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockChangeEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ChunkTranslation;
import org.cloudburstmc.math.vector.Vector3i;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The chunk columns and block updates of the MCProtocolLib 1.19.4 release, whose codec reads a section and whose block
 * changes name their positions as vectors.
 */
public final class ChunkPackets extends ChunkTranslation {
	private final MinecraftCodecHelper helper = MinecraftCodec.CODEC.getHelperFactory().get();

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
		ByteBuf buf = Unpooled.wrappedBuffer(data);
		List<LevelChunkSection> sections = new ArrayList<>(count);
		for (int section = 0; section < count; section++)
			sections.add(section(helper.readChunkSection(buf, UNWRITTEN_GLOBAL_BITS).getChunkData()));

		return sections;
	}

	@Override
	protected @NotNull BlockPosition position(@NotNull BlockChangeEntry entry) {
		Vector3i position = entry.getPosition();
		return BlockPosition.builder().x(position.getX()).y(position.getY()).z(position.getZ()).build();
	}
}
