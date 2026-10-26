package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

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
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The chunk columns and block updates of the MCProtocolLib releases from 1.16.5 until 1.17.1, which read a column's
 * sections themselves and leave out the sections of only air; Minecraft 1.17 lists its sections by a mask as long as
 * the bits it carries, so a column may name sections above its level. A column without biomes is part of a column,
 * which only Minecraft 1.16 sends and the client applies block by block, so it becomes an update of every block it
 * carries.
 */
public final class WorldPackets implements PacketTranslation<Packet> {
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
			case ServerUnloadChunkPacket unload -> new ClientboundForgetLevelChunk(new ChunkPosition(unload.getX(),
					unload.getZ()));
			case ServerBlockChangePacket change -> new ClientboundBlockUpdate(change(change.getRecord()));
			case ServerMultiBlockChangePacket changes -> new ClientboundSectionBlocksUpdate(
					Arrays.stream(changes.getRecords()).map(WorldPackets::change).toList());
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private static ClientboundPacket column(Column column) {
		Chunk[] chunks = column.getChunks();
		if (column.getBiomeData() == null) return blocks(column.getX(), column.getZ(), chunks);

		List<LevelChunkSection> sections = new ArrayList<>(chunks.length);
		for (Chunk chunk : chunks) sections.add(chunk == null ? LevelChunkSection.EMPTY : section(chunk));

		return new ClientboundLevelChunkWithLight(new ChunkPosition(column.getX(), column.getZ()), List.copyOf(sections));
	}

	/**
	 * Lists every block of the sections part of a column carries, as the client sets them one by one.
	 */
	private static ClientboundSectionBlocksUpdate blocks(int chunkX, int chunkZ, Chunk[] chunks) {
		List<BlockChange> changes = new ArrayList<>();
		for (int index = 0; index < chunks.length; index++) {
			Chunk chunk = chunks[index];
			if (chunk == null) continue;

			LevelChunkSection section = section(chunk);
			for (int y = 0; y < LevelChunkSection.SIZE; y++)
				for (int z = 0; z < LevelChunkSection.SIZE; z++)
					for (int x = 0; x < LevelChunkSection.SIZE; x++)
						changes.add(new BlockChange(BlockPosition.builder()
								.x((chunkX << 4) + x)
								.y((index << 4) + y)
								.z((chunkZ << 4) + z)
								.build(), section.blockState(x, y, z)));
		}

		return new ClientboundSectionBlocksUpdate(List.copyOf(changes));
	}

	private static LevelChunkSection section(Chunk chunk) {
		Palette palette = chunk.getPalette();
		BitStorage storage = chunk.getStorage();
		if (palette instanceof GlobalPalette) return LevelChunkSection.direct(storage.getBitsPerEntry(), storage.getData());

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
