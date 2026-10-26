package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

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
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.BitStorage;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.GlobalPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.ListPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.MapPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.Palette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.PaletteType;
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
 * The chunk columns and block updates of the MCProtocolLib releases from 1.21.11 on, whose column carries every
 * section of its level as bytes. Since Minecraft 1.21.5 a section's packed entries have no length of their own: the
 * bits per entry each container announces give it. The release's section reader also wants the sizes of the block
 * state and biome registries, which it needs only to grow a section it changes; the translation therefore reads each
 * container with the release's own palettes and storage by the bits it announces, as that reader does, and skips the
 * biomes, since the protocol models none. Each release's {@code WorldPackets} skips the counts a section starts with,
 * to which Minecraft 26.1 added the fluid count.
 */
public abstract class WorldTranslation implements PacketTranslation<Packet> {
	private final ConnectionLevel level;

	/**
	 * Creates the translation of one connection.
	 *
	 * @param level the connection's level, whose section count a column's bytes hold
	 */
	protected WorldTranslation(@NotNull ConnectionLevel level) {
		this.level = level;
	}

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundBlockUpdate.class,
				ClientboundForgetLevelChunk.class,
				ClientboundLevelChunkWithLight.class,
				ClientboundSectionBlocksUpdate.class
		);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundLevelChunkWithLightPacket chunk -> new ClientboundLevelChunkWithLight(
					new ChunkPosition(chunk.getX(), chunk.getZ()), sections(chunk.getChunkData()));
			case ClientboundForgetLevelChunkPacket forget -> new ClientboundForgetLevelChunk(
					new ChunkPosition(forget.getX(), forget.getZ()));
			case ClientboundBlockUpdatePacket update -> new ClientboundBlockUpdate(change(update.getEntry()));
			case ClientboundSectionBlocksUpdatePacket update -> new ClientboundSectionBlocksUpdate(
					Arrays.stream(update.getEntries()).map(WorldTranslation::change).toList());
			default -> null;
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Skips the counts a section starts with, ahead of its block states.
	 *
	 * @param buf the column's bytes at the start of a section
	 */
	protected abstract void skipCounts(@NotNull ByteBuf buf);

	/**
	 * Reads the sections of a column from the lowest section up, as many as the level has: each holds its counts,
	 * its block states and its biomes.
	 */
	private List<LevelChunkSection> sections(byte[] data) {
		ByteBuf buf = Unpooled.wrappedBuffer(data);
		int count = level.current().sectionCount();
		List<LevelChunkSection> sections = new ArrayList<>(count);
		for (int section = 0; section < count; section++) {
			skipCounts(buf);
			sections.add(section(container(buf, PaletteType.BLOCK_STATE)));
			container(buf, PaletteType.BIOME);
		}

		return List.copyOf(sections);
	}

	/**
	 * Reads one paletted container: its bits per entry, the palette they select and, unless one value fills the
	 * container, its packed entries.
	 */
	private static Container container(ByteBuf buf, PaletteType type) {
		int bitsPerEntry = buf.readUnsignedByte();
		if (bitsPerEntry == 0) return new Container(new SingletonPalette(MinecraftTypes.readVarInt(buf)), null);

		Palette palette = palette(buf, type, bitsPerEntry);
		BitStorage storage = new BitStorage(bitsPerEntry, type.getStorageSize());
		MinecraftTypes.readFixedSizeLongArray(buf, storage.getData());

		return new Container(palette, storage);
	}

	private static Palette palette(ByteBuf buf, PaletteType type, int bitsPerEntry) {
		if (bitsPerEntry <= type.getMinBitsPerEntry()) return new ListPalette(bitsPerEntry, buf);
		if (bitsPerEntry <= type.getMaxBitsPerEntry()) return new MapPalette(bitsPerEntry, buf);

		return GlobalPalette.INSTANCE;
	}

	private static LevelChunkSection section(Container states) {
		Palette palette = states.palette();
		BitStorage storage = states.storage();
		if (storage == null) return LevelChunkSection.uniform(palette.idToState(0));
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

	/**
	 * A container's palette and its packed entries, which a container of one value has none of.
	 */
	private record Container(Palette palette, @Nullable BitStorage storage) {
	}
}
