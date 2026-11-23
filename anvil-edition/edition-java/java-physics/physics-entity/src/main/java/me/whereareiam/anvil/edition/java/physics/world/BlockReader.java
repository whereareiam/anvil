package me.whereareiam.anvil.edition.java.physics.world;

import me.whereareiam.anvil.edition.java.data.block.BlockRegistry;
import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionShape;
import me.whereareiam.anvil.edition.java.physics.world.shape.BlockShape;
import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.WorldView;
import me.whereareiam.anvil.edition.java.protocol.model.world.Level;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The blocks the player's client knows, as vanilla's client level answers movement: the state of every block of the
 * loaded chunk columns by the release's block data, and void air wherever the client has no block, outside the level's
 * heights or in a column it does not have. A tick reads the world at one moment through {@link #read}, as the vanilla
 * client runs its packets between ticks.
 *
 * <p>The reader is not thread-safe; its owner serializes ticks.</p>
 */
public final class BlockReader {
	private final @NotNull JavaWorld world;
	private final @NotNull BlockRegistry registry;
	private final @NotNull BlockState voidAir;
	private final @NotNull Map<CollisionShape, BlockShape> shapes = new HashMap<>();
	private @Nullable WorldView view;

	/**
	 * Creates the reader of a player's world.
	 *
	 * @param world the world the player's client knows
	 * @param registry the block data of the player's protocol
	 */
	public BlockReader(@NotNull JavaWorld world, @NotNull BlockRegistry registry) {
		this.world = world;
		this.registry = registry;
		this.voidAir = registry.state(registry.block("minecraft:void_air").orElseThrow().defaultStateId());
	}

	/**
	 * Runs a reader that sees the world at one moment: no packet changes the world while it runs.
	 *
	 * @param reader what reads the blocks through this reader
	 */
	public void read(@NotNull Runnable reader) {
		world.read(current -> {
			view = current;
			try {
				reader.run();
			} finally {
				view = null;
			}
			return null;
		});
	}

	/**
	 * Returns the state of a block, void air where the client has none.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @return the state
	 * @throws IllegalArgumentException when the block's state is not one of the protocol's, such as a modded block's,
	 * whose collision the client's physics cannot know
	 */
	public @NotNull BlockState state(int x, int y, int z) {
		OptionalInt state = view().blockState(x, y, z);
		if (state.isEmpty()) return voidAir;

		return registry.state(state.getAsInt());
	}

	/**
	 * Returns the voxel shape of a collision shape the block data records.
	 *
	 * @param collision the collision shape
	 * @return the voxel shape, built once
	 */
	public @NotNull BlockShape shape(@NotNull CollisionShape collision) {
		return shapes.computeIfAbsent(collision, BlockShape::of);
	}

	/**
	 * Tells whether the client has the chunk column holding a block column, as {@code Level.hasChunkAt} does.
	 *
	 * @param x the block column's east-west coordinate
	 * @param z the block column's north-south coordinate
	 * @return whether the column is loaded
	 */
	public boolean hasChunkAt(int x, int z) {
		return view().isLoaded(ChunkPosition.containing(x, z));
	}

	/**
	 * Tells whether the client has every chunk column a range of block columns touches, as {@code hasChunksAt} does.
	 *
	 * @param fromX the lowest east-west block coordinate
	 * @param fromZ the lowest north-south block coordinate
	 * @param toX the highest east-west block coordinate
	 * @param toZ the highest north-south block coordinate
	 * @return whether every column is loaded
	 */
	public boolean hasChunksAt(int fromX, int fromZ, int toX, int toZ) {
		for (int chunkX = fromX >> 4; chunkX <= toX >> 4; chunkX++)
			for (int chunkZ = fromZ >> 4; chunkZ <= toZ >> 4; chunkZ++)
				if (!view().isLoaded(new ChunkPosition(chunkX, chunkZ))) return false;

		return true;
	}

	/**
	 * Returns the lowest height of the level the client is in, as {@code Level.getMinY} does.
	 *
	 * @return the lowest height, or {@code 0} before the client knows a level
	 */
	public int minY() {
		return view().level().map(Level::minY).orElse(0);
	}

	/**
	 * Tells whether lava pushes entities fast in the client's level, as its dimension type says
	 * ({@code DimensionType.ultraWarm} before 1.21.11, the {@code minecraft:gameplay/fast_lava} environment attribute
	 * from 1.21.11).
	 *
	 * @return whether lava pushes fast, or {@code false} before the client knows a level
	 */
	public boolean fastLava() {
		return view().level().map(level -> level.type().fastLava()).orElse(false);
	}

	private @NotNull WorldView view() {
		WorldView current = view;
		if (current == null) throw new IllegalStateException("The client's blocks are read outside a tick");

		return current;
	}
}
