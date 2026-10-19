package me.whereareiam.anvil.protocol.java.worker;

import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockChange;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.model.world.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSectionBlocksUpdate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The chunk columns one player's client received, which its {@link JavaPacketDispatcher} feeds with every packet
 * before the packet's listeners see it. Packets arrive on the connection's packet thread, while extensions read from
 * any thread: the level is published through a volatile field, the columns through a concurrent map, and each column
 * guards its own sections. A column keeps its sections as the server sent them and copies a section into a plain
 * array of states only when a block update changes it.
 */
final class JavaChunkStore implements JavaWorld {
	private static final int SECTION_SHIFT = 4;
	private static final int SECTION_MASK = LevelChunkSection.SIZE - 1;

	private final Map<ChunkPosition, Column> columns = new ConcurrentHashMap<>();
	private volatile @Nullable Dimension dimension;

	@Override
	public @NotNull Optional<Dimension> dimension() {
		return Optional.ofNullable(dimension);
	}

	@Override
	public boolean isLoaded(@NotNull ChunkPosition chunk) {
		return columns.containsKey(chunk);
	}

	@Override
	public @NotNull Set<ChunkPosition> loadedChunks() {
		return Set.copyOf(columns.keySet());
	}

	@Override
	public @NotNull OptionalInt blockState(int x, int y, int z) {
		Column column = columns.get(ChunkPosition.containing(x, z));
		if (column == null || !column.dimension.contains(y)) return OptionalInt.empty();

		return OptionalInt.of(column.blockState(x & SECTION_MASK, y, z & SECTION_MASK));
	}

	/**
	 * Applies a packet the player received to the view; packets that change nothing of the world are ignored.
	 *
	 * @param packet received packet
	 */
	void receive(@NotNull ClientboundPacket packet) {
		switch (packet) {
			case ClientboundLogin login -> enter(login.getDimension());
			case ClientboundRespawn respawn -> respawn(respawn.getDimension());
			case ClientboundStartConfiguration ignored -> clear();
			case ClientboundLevelChunkWithLight chunk -> load(chunk);
			case ClientboundForgetLevelChunk forget -> columns.remove(forget.getPosition());
			case ClientboundBlockUpdate update -> change(update.getChange());
			case ClientboundSectionBlocksUpdate update -> update.getChanges().forEach(this::change);
			default -> {
			}
		}
	}

	/**
	 * Forgets the level and every column, as the client does when it leaves the level.
	 */
	void clear() {
		dimension = null;
		columns.clear();
	}

	private void enter(Dimension entered) {
		columns.clear();
		dimension = entered;
	}

	private void respawn(Dimension respawned) {
		Dimension current = dimension;
		if (current == null || !current.getKey().equals(respawned.getKey())) columns.clear();

		dimension = respawned;
	}

	private void load(ClientboundLevelChunkWithLight chunk) {
		Dimension current = dimension;
		if (current == null) return;

		columns.put(chunk.getPosition(), new Column(current, chunk.getSections()));
	}

	private void change(BlockChange change) {
		BlockPosition position = change.getPosition();
		Column column = columns.get(ChunkPosition.containing(position.getX(), position.getZ()));
		if (column == null || !column.dimension.contains(position.getY())) return;

		column.change(position.getX() & SECTION_MASK, position.getY(), position.getZ() & SECTION_MASK,
				change.getBlockState());
	}

	/**
	 * The sections of one column from the lowest section of its level up; the column keeps the level it was sent in,
	 * so a reader never places its sections by the range of a level entered since. A section the server sent stays in
	 * its paletted form until a block update changes it, which copies it into an array of its states.
	 */
	private static final class Column {
		private final Dimension dimension;
		private final LevelChunkSection[] sections;
		private final int[][] changed;

		private Column(Dimension dimension, List<LevelChunkSection> received) {
			int sectionCount = dimension.sectionCount();
			this.dimension = dimension;
			this.sections = new LevelChunkSection[sectionCount];
			this.changed = new int[sectionCount][];
			for (int index = 0; index < sectionCount; index++)
				sections[index] = index < received.size() ? received.get(index) : LevelChunkSection.EMPTY;
		}

		/**
		 * Returns a block's state by its offsets in the column and its height in the level.
		 */
		private synchronized int blockState(int x, int height, int z) {
			int section = (height - dimension.getMinY()) >> SECTION_SHIFT;
			int y = height & SECTION_MASK;
			int[] states = changed[section];
			if (states != null) return states[index(x, y, z)];

			return sections[section].blockState(x, y, z);
		}

		private synchronized void change(int x, int height, int z, int blockState) {
			int section = (height - dimension.getMinY()) >> SECTION_SHIFT;
			int[] states = changed[section];
			if (states == null) {
				states = sections[section].blockStates();
				changed[section] = states;
			}

			states[index(x, height & SECTION_MASK, z)] = blockState;
		}

		private static int index(int x, int y, int z) {
			return (y << 8) | (z << 4) | x;
		}
	}
}
