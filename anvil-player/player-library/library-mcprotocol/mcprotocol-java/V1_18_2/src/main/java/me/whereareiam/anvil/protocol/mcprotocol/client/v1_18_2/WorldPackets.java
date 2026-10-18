package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.chunk.BitStorage;
import com.github.steveice10.mc.protocol.data.game.chunk.DataPalette;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.GlobalPalette;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.Palette;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.PaletteType;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.SingletonPalette;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockChangeEntry;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import com.github.steveice10.packetlib.io.NetInput;
import com.github.steveice10.packetlib.io.stream.StreamNetInput;
import com.github.steveice10.packetlib.packet.Packet;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The chunk columns and block updates of the MCProtocolLib 1.18.2 release, whose column carries every section of its
 * level as bytes. Each section holds its block count, its block states and its biomes; the block states are read with
 * the release's own palette reader, which the release sizes by its own bits of a direct palette, and the biomes are
 * skipped, since the protocol models none.
 */
public final class WorldPackets implements PacketTranslation<Packet> {
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
	 * Reads the sections of a column from the lowest section up, as many as the level has; the bytes may end in
	 * padding, since the game sizes them by more than the sections take.
	 */
	private List<LevelChunkSection> sections(byte[] data) {
		NetInput in = new StreamNetInput(new ByteArrayInputStream(data));
		int count = level.current().sectionCount();
		List<LevelChunkSection> sections = new ArrayList<>(count);
		try {
			for (int section = 0; section < count; section++) {
				in.readShort();
				sections.add(section(DataPalette.read(in, PaletteType.CHUNK, DataPalette.GLOBAL_PALETTE_BITS_PER_ENTRY)));
				skipBiomes(in);
			}
		} catch (IOException malformed) {
			throw new UncheckedIOException("The server sent a malformed chunk column", malformed);
		}

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

	/**
	 * Skips the biomes of a section: their bits per entry, their palette, which a single biome, a list or the global
	 * registry gives, and their packed entries.
	 */
	private static void skipBiomes(NetInput in) throws IOException {
		int bitsPerEntry = in.readUnsignedByte();
		if (bitsPerEntry == 0) in.readVarInt();
		if (bitsPerEntry > 0 && bitsPerEntry <= PaletteType.BIOME.getMaxBitsPerEntry()) {
			int entries = in.readVarInt();
			for (int entry = 0; entry < entries; entry++) in.readVarInt();
		}

		in.readLongs(in.readVarInt());
	}

	private static BlockChange change(BlockChangeEntry entry) {
		Position position = entry.getPosition();
		return new BlockChange(BlockPosition.builder()
				.x(position.getX())
				.y(position.getY())
				.z(position.getZ())
				.build(), entry.getBlock());
	}
}
