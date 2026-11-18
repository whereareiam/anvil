package me.whereareiam.anvil.edition.java.protocol.worker;

import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.WorldView;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.Level;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.BlockChange;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundSectionBlocksUpdate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The world one player's client knows, which its {@link JavaPacketDispatcher} feeds with every packet before the
 * packet's listeners see it. Packets arrive on the connection's packet thread while extensions read from any thread,
 * so one lock guards the whole world: a packet applies under the write lock, each read and each
 * {@link #read(Function) reader} under the read lock. The level is kept from the start; chunk columns only once
 * {@link #storeChunks()} was called. A column keeps its sections as the server sent them and copies a section into a
 * plain array of states only when a block update changes it.
 */
final class JavaWorldStore implements JavaWorld {
	/**
	 * The packets that change the chunks the world keeps, which a release need not decode until the world keeps them.
	 */
	static final Set<Class<? extends ClientboundPacket>> CHUNK_PACKETS = Set.of(
			ClientboundBlockUpdate.class,
			ClientboundForgetLevelChunk.class,
			ClientboundLevelChunkWithLight.class,
			ClientboundSectionBlocksUpdate.class
	);
	private static final int SECTION_SHIFT = 4;
	private static final int SECTION_MASK = LevelChunkSection.SIZE - 1;

	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private final Map<ChunkPosition, Column> columns = new HashMap<>();
	private final WorldView view = new LockedView();
	private volatile boolean storing;
	private @Nullable Level level;

	@Override
	public @NotNull Optional<Level> level() {
		return locked(view::level);
	}

	@Override
	public boolean isLoaded(@NotNull ChunkPosition chunk) {
		return locked(() -> view.isLoaded(chunk));
	}

	@Override
	public @NotNull OptionalInt blockState(int x, int y, int z) {
		return locked(() -> view.blockState(x, y, z));
	}

	@Override
	public @NotNull Set<ChunkPosition> loadedChunks() {
		return locked(() -> Set.copyOf(columns.keySet()));
	}

	@Override
	public <T> T read(@NotNull Function<? super WorldView, ? extends T> reader) {
		return locked(() -> reader.apply(view));
	}

	/**
	 * Makes the world keep the chunk columns the server sends from now on.
	 */
	void storeChunks() {
		storing = true;
	}

	/**
	 * Tells whether the world wants a packet decoded: the packets of its chunks, once it keeps them.
	 *
	 * @param type packet class
	 * @return whether the world applies packets of that class
	 */
	boolean wants(@NotNull Class<? extends ClientboundPacket> type) {
		return storing && CHUNK_PACKETS.contains(type);
	}

	/**
	 * Applies a packet the player received to the world, whole; packets that change nothing of the world are ignored.
	 *
	 * @param packet received packet
	 */
	void apply(@NotNull ClientboundPacket packet) {
		switch (packet) {
			case ClientboundLogin login -> written(() -> enter(login));
			case ClientboundRespawn respawn -> written(() -> respawn(respawn));
			case ClientboundStartConfiguration ignored -> clear();
			case ClientboundLevelChunkWithLight chunk -> written(() -> load(chunk));
			case ClientboundForgetLevelChunk forget -> written(() -> columns.remove(forget.getPosition()));
			case ClientboundBlockUpdate update -> written(() -> change(update.getChange()));
			case ClientboundSectionBlocksUpdate update -> written(() -> update.getChanges().forEach(this::change));
			default -> {
			}
		}
	}

	/**
	 * Forgets the level and every column, as the client does when it leaves the level.
	 */
	void clear() {
		written(() -> {
			level = null;
			columns.clear();
		});
	}

	private void enter(ClientboundLogin login) {
		columns.clear();
		level = new Level(login.getDimension(), login.getDimensionType());
	}

	private void respawn(ClientboundRespawn respawn) {
		if (level == null || !level.getKey().equals(respawn.getDimension())) columns.clear();

		level = new Level(respawn.getDimension(), respawn.getDimensionType());
	}

	private void load(ClientboundLevelChunkWithLight chunk) {
		if (!storing || level == null) return;

		columns.put(chunk.getPosition(), new Column(level, chunk.getSections()));
	}

	private void change(BlockChange change) {
		BlockPosition position = change.getPosition();
		Column column = columns.get(ChunkPosition.containing(position.getX(), position.getZ()));
		if (column == null || !column.level.contains(position.getY())) return;

		column.change(position.getX() & SECTION_MASK, position.getY(), position.getZ() & SECTION_MASK,
				change.getBlockState());
	}

	private <T> T locked(Supplier<T> read) {
		Lock reading = lock.readLock();
		reading.lock();
		try {
			return read.get();
		} finally {
			reading.unlock();
		}
	}

	private void written(Runnable write) {
		Lock writing = lock.writeLock();
		writing.lock();
		try {
			write.run();
		} finally {
			writing.unlock();
		}
	}

	/**
	 * The world as a reader sees it; its caller holds the read lock.
	 */
	private final class LockedView implements WorldView {
		@Override
		public @NotNull Optional<Level> level() {
			return Optional.ofNullable(level);
		}

		@Override
		public boolean isLoaded(@NotNull ChunkPosition chunk) {
			return columns.containsKey(chunk);
		}

		@Override
		public @NotNull OptionalInt blockState(int x, int y, int z) {
			Column column = columns.get(ChunkPosition.containing(x, z));
			if (column == null || !column.level.contains(y)) return OptionalInt.empty();

			return OptionalInt.of(column.blockState(x & SECTION_MASK, y, z & SECTION_MASK));
		}
	}

	/**
	 * The sections of one column from the lowest section of its level up; the column keeps the level it was sent in,
	 * so a reader never places its sections by the heights of a level entered since. Sections the packet left out hold
	 * only air, and sections it lists above the level are dropped.
	 */
	private static final class Column {
		private final Level level;
		private final LevelChunkSection[] sections;
		private final int[][] changed;

		private Column(Level level, List<LevelChunkSection> received) {
			int sectionCount = level.sectionCount();
			this.level = level;
			this.sections = new LevelChunkSection[sectionCount];
			this.changed = new int[sectionCount][];
			for (int index = 0; index < sectionCount; index++)
				sections[index] = index < received.size() ? received.get(index) : LevelChunkSection.EMPTY;
		}

		/**
		 * Returns a block's state by its offsets in the column and its height in the level.
		 */
		private int blockState(int x, int height, int z) {
			int section = (height - level.minY()) >> SECTION_SHIFT;
			int[] states = changed[section];
			if (states != null) return states[index(x, height & SECTION_MASK, z)];

			return sections[section].blockState(x, height & SECTION_MASK, z);
		}

		private void change(int x, int height, int z, int blockState) {
			int section = (height - level.minY()) >> SECTION_SHIFT;
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
