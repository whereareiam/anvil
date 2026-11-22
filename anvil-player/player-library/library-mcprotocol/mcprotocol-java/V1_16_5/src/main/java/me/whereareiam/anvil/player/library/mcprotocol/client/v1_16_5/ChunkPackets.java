package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.chunk.BitStorage;
import com.github.steveice10.mc.protocol.data.game.chunk.Chunk;
import com.github.steveice10.mc.protocol.data.game.chunk.Column;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.GlobalPalette;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.Palette;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockChangeRecord;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerBlockChangePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerMultiBlockChangePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUnloadChunkPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
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
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The chunk columns and block updates of the MCProtocolLib releases from 1.16.5 until 1.17.1, whose library reads a
 * column's sections itself and leaves out the sections of only air; Minecraft 1.17 lists its sections by a mask as
 * long as the bits it carries, so a column may name sections above its level. A column without biomes is part of a
 * column, which only Minecraft 1.16 sends and the client applies section by section, so it becomes an update of every
 * block of the sections it carries. Nothing is decoded while the session's demand does not want it.
 */
public final class ChunkPackets implements PacketTranslation<Packet> {
	private final PacketDemand demand;

	/**
	 * Creates the translation of one connection.
	 *
	 * @param demand the packets the connection wants decoded
	 */
	public ChunkPackets(@NotNull PacketDemand demand) {
		this.demand = demand;
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
			case ServerChunkDataPacket chunk -> column(chunk.getColumn());
			case ServerUnloadChunkPacket unload when demand.wants(ClientboundForgetLevelChunk.class) ->
					new ClientboundForgetLevelChunk(new ChunkPosition(unload.getX(), unload.getZ()));
			case ServerBlockChangePacket change when demand.wants(ClientboundBlockUpdate.class) ->
					new ClientboundBlockUpdate(change(change.getRecord()));
			case ServerMultiBlockChangePacket changes when demand.wants(ClientboundSectionBlocksUpdate.class) ->
					new ClientboundSectionBlocksUpdate(Arrays.stream(changes.getRecords())
							.map(ChunkPackets::change)
							.toList());
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private @Nullable ClientboundPacket column(Column column) {
		boolean whole = column.getBiomeData() != null;
		if (!demand.wants(whole ? ClientboundLevelChunkWithLight.class : ClientboundSectionBlocksUpdate.class))
			return null;

		try {
			if (!whole) return blocks(column.getX(), column.getZ(), column.getChunks());

			List<LevelChunkSection> sections = new ArrayList<>(column.getChunks().length);
			for (Chunk chunk : column.getChunks())
				sections.add(chunk == null ? LevelChunkSection.EMPTY : section(chunk));

			return new ClientboundLevelChunkWithLight(new ChunkPosition(column.getX(), column.getZ()),
					List.copyOf(sections));
		} catch (IllegalArgumentException malformed) {
			System.err.println("[Anvil] Warning: the server sent an unreadable chunk column at " + column.getX() + " "
					+ column.getZ() + ", which the player's world leaves out: " + malformed.getMessage());
			return null;
		}
	}

	/**
	 * Lists every block of the sections part of a column carries, as the client replaces those sections.
	 */
	private static ClientboundSectionBlocksUpdate blocks(int chunkX, int chunkZ, Chunk[] chunks) {
		List<BlockChange> changes = new ArrayList<>();
		for (int index = 0; index < chunks.length; index++) {
			Chunk chunk = chunks[index];
			if (chunk == null) continue;

			int[] states = section(chunk).blockStates();
			for (int block = 0; block < states.length; block++)
				changes.add(new BlockChange(BlockPosition.builder()
						.x((chunkX << 4) + (block & 15))
						.y((index << 4) + (block >> 8))
						.z((chunkZ << 4) + ((block >> 4) & 15))
						.build(), states[block]));
		}

		return new ClientboundSectionBlocksUpdate(List.copyOf(changes));
	}

	private static LevelChunkSection section(Chunk chunk) {
		Palette palette = chunk.getPalette();
		BitStorage storage = chunk.getStorage();
		if (palette instanceof GlobalPalette)
			return LevelChunkSection.direct(storage.getBitsPerEntry(), storage.getData());

		int[] states = new int[palette.size()];
		for (int entry = 0; entry < states.length; entry++) states[entry] = palette.idToState(entry);

		return LevelChunkSection.paletted(states, storage.getBitsPerEntry(), storage.getData());
	}

	private static BlockChange change(BlockChangeRecord record) {
		Position position = record.getPosition();
		return new BlockChange(BlockPosition.builder()
				.x(position.getX())
				.y(position.getY())
				.z(position.getZ())
				.build(), record.getBlock());
	}
}
