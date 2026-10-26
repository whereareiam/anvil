package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockChange;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSectionBlocksUpdate;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodecHelper;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.BitStorage;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.GlobalPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.Palette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.SingletonPalette;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The chunk columns and block updates of the MCProtocolLib 1.20.6 and 1.21.1 releases, whose column carries every
 * section of its level as bytes, which the release's own section reader reads.
 */
public final class WorldPackets implements PacketTranslation<Packet> {
	private final MinecraftCodecHelper helper = MinecraftCodec.CODEC.getHelperFactory().get();
	private final ConnectionLevel level;

	/**
	 * Creates the translation of one connection.
	 *
	 * @param level the connection's level, whose section count a column's bytes hold
	 */
	public WorldPackets(@NotNull ConnectionLevel level) {
		this.level = level;
	}

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundBlockUpdate.class,
				ClientboundForgetLevelChunk.class,
				ClientboundLevelChunkWithLight.class,
				ClientboundSectionBlocksUpdate.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundLevelChunkWithLightPacket chunk -> new ClientboundLevelChunkWithLight(
					new ChunkPosition(chunk.getX(), chunk.getZ()), sections(chunk.getChunkData()));
			case ClientboundForgetLevelChunkPacket forget -> new ClientboundForgetLevelChunk(
					new ChunkPosition(forget.getX(), forget.getZ()));
			case ClientboundBlockUpdatePacket update -> new ClientboundBlockUpdate(change(update.getEntry()));
			case ClientboundSectionBlocksUpdatePacket update -> new ClientboundSectionBlocksUpdate(
					Arrays.stream(update.getEntries()).map(WorldPackets::change).toList());
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads the sections of a column from the lowest section up, as many as the level has.
	 */
	private List<LevelChunkSection> sections(byte[] data) {
		ByteBuf buf = Unpooled.wrappedBuffer(data);
		int count = level.current().sectionCount();
		List<LevelChunkSection> sections = new ArrayList<>(count);
		for (int section = 0; section < count; section++)
			sections.add(section(helper.readChunkSection(buf).getChunkData()));

		return List.copyOf(sections);
	}

	private static LevelChunkSection section(DataPalette states) {
		Palette palette = states.getPalette();
		BitStorage storage = states.getStorage();
		if (palette instanceof SingletonPalette) return LevelChunkSection.uniform(palette.idToState(0));
		if (palette instanceof GlobalPalette) return LevelChunkSection.direct(storage.getBitsPerEntry(), storage.getData());

		int[] entries = new int[palette.size()];
		for (int entry = 0; entry < entries.length; entry++) entries[entry] = palette.idToState(entry);

		return LevelChunkSection.paletted(entries, storage.getBitsPerEntry(), storage.getData());
	}

	private static BlockChange change(BlockChangeEntry entry) {
		Vector3i position = entry.getPosition();
		return new BlockChange(BlockPosition.builder()
				.x(position.getX())
				.y(position.getY())
				.z(position.getZ())
				.build(), entry.getBlock());
	}
}
