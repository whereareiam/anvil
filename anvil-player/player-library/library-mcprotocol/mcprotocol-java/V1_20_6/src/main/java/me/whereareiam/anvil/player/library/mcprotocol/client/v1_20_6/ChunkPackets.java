package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodecHelper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The chunk columns and block updates of the MCProtocolLib releases from 1.20.5 until 1.21.1, whose codec reads a
 * section with the sizes of its own global palettes.
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
	protected @NotNull List<LevelChunkSection> sections(byte @NotNull [] data, int count) {
		ByteBuf buf = Unpooled.wrappedBuffer(data);
		List<LevelChunkSection> sections = new ArrayList<>(count);
		for (int section = 0; section < count; section++)
			sections.add(section(helper.readChunkSection(buf).getChunkData()));

		return sections;
	}
}
