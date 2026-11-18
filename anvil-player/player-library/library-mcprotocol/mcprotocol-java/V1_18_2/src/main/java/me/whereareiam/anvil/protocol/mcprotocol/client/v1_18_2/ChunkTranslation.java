package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.chunk.BitStorage;
import com.github.steveice10.mc.protocol.data.game.chunk.DataPalette;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.GlobalPalette;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.Palette;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.SingletonPalette;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockChangeEntry;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.Level;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.BlockChange;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundSectionBlocksUpdate;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The chunk columns and block updates of the MCProtocolLib releases from 1.18.2 until 1.19.4, whose column carries
 * every section of its level as bytes. Each release's {@code ChunkPackets} reads the sections with the release's own
 * section reader and names a block change's position, because MCProtocolLib 1.19 moved the reader into its codec and
 * changed the position's type. A column the release cannot read is left out with a warning instead of disconnecting
 * the player, and nothing is decoded while the session's demand does not want it.
 */
public abstract class ChunkTranslation implements PacketTranslation<Packet> {
	/**
	 * The global palette bits the release keeps with a section only to grow it when it is written, which the
	 * translation never does; a read section's entries take the bits the server announced.
	 */
	protected static final int UNWRITTEN_GLOBAL_BITS = 1;

	private final ConnectionLevel level;
	private final PacketDemand demand;

	/**
	 * Creates the translation of one connection.
	 *
	 * @param level the connection's level, whose section count a column's bytes hold
	 * @param demand the packets the connection wants decoded
	 */
	protected ChunkTranslation(@NotNull ConnectionLevel level, @NotNull PacketDemand demand) {
		this.level = level;
		this.demand = demand;
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
			case ClientboundLevelChunkWithLightPacket chunk when demand.wants(ClientboundLevelChunkWithLight.class) ->
					column(new ChunkPosition(chunk.getX(), chunk.getZ()), chunk.getChunkData());
			case ClientboundForgetLevelChunkPacket forget when demand.wants(ClientboundForgetLevelChunk.class) ->
					new ClientboundForgetLevelChunk(new ChunkPosition(forget.getX(), forget.getZ()));
			case ClientboundBlockUpdatePacket update when demand.wants(ClientboundBlockUpdate.class) ->
					new ClientboundBlockUpdate(change(update.getEntry()));
			case ClientboundSectionBlocksUpdatePacket update when demand.wants(ClientboundSectionBlocksUpdate.class) ->
					new ClientboundSectionBlocksUpdate(Arrays.stream(update.getEntries()).map(this::change).toList());
			default -> null;
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads the sections of a column from the lowest section up with the release's own section reader.
	 *
	 * @param data the column's bytes, which may end in padding
	 * @param count the sections of the column's level
	 * @return the sections, from the lowest up
	 * @throws IOException when the bytes end before the sections do
	 */
	protected abstract @NotNull List<LevelChunkSection> sections(byte @NotNull [] data, int count) throws IOException;

	/**
	 * Returns the position of a changed block.
	 *
	 * @param entry the release's block change
	 * @return the block's position
	 */
	protected abstract @NotNull BlockPosition position(@NotNull BlockChangeEntry entry);

	/**
	 * Translates the block states of one section the release read.
	 *
	 * @param states the release's block states of the section
	 * @return the section
	 * @throws IllegalArgumentException when the states do not fill a section
	 */
	protected static @NotNull LevelChunkSection section(@NotNull DataPalette states) {
		Palette palette = states.getPalette();
		BitStorage storage = states.getStorage();
		if (palette instanceof SingletonPalette) return LevelChunkSection.uniform(palette.idToState(0));
		if (palette instanceof GlobalPalette)
			return LevelChunkSection.direct(storage.getBitsPerEntry(), storage.getData());

		int[] entries = new int[palette.size()];
		for (int entry = 0; entry < entries.length; entry++) entries[entry] = palette.idToState(entry);

		return LevelChunkSection.paletted(entries, storage.getBitsPerEntry(), storage.getData());
	}

	private @Nullable ClientboundPacket column(ChunkPosition position, byte[] data) {
		Optional<Level> current = level.current();
		if (current.isEmpty()) return unreadable(position, "the server sent it before it named the level");

		try {
			List<LevelChunkSection> sections = sections(data, current.get().sectionCount());
			return new ClientboundLevelChunkWithLight(position, List.copyOf(sections));
		} catch (IOException | IndexOutOfBoundsException | IllegalArgumentException malformed) {
			return unreadable(position, String.valueOf(malformed.getMessage()));
		}
	}

	private BlockChange change(BlockChangeEntry entry) {
		return new BlockChange(position(entry), entry.getBlock());
	}

	private static @Nullable ClientboundPacket unreadable(ChunkPosition position, String reason) {
		System.err.println("[Anvil] Warning: the player's world leaves out the chunk column at " + position.getX() + " "
				+ position.getZ() + ", which it cannot read: " + reason);
		return null;
	}
}
