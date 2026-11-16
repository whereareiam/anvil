package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ChunkTranslation;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The chunk columns and block updates of the MCProtocolLib releases from 1.21.11 on, whose static types read a section
 * by the sizes of the global palettes its caller names. Since Minecraft 1.21.5 a section's entries carry no length;
 * the reader sizes them by the bits the server announces, which a vanilla server announces as vanilla's palettes
 * store them.
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
	protected @NotNull List<LevelChunkSection> sections(byte @NotNull [] data, int count) {
		ByteBuf buf = Unpooled.wrappedBuffer(data);
		List<LevelChunkSection> sections = new ArrayList<>(count);
		for (int section = 0; section < count; section++)
			sections.add(section(MinecraftTypes.readChunkSection(buf, UNWRITTEN_GLOBAL_BITS, UNWRITTEN_GLOBAL_BITS)
					.getBlockData()));

		return sections;
	}
}
