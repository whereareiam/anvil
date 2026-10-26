package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.data.BlockRegistry;
import me.whereareiam.anvil.protocol.java.data.model.BlockState;
import me.whereareiam.anvil.protocol.java.data.model.CollisionShape;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The blocks the player's client knows, as movement reads them: the state of every block of the loaded chunk columns
 * by the release's block data, and void air wherever the client has no block, outside the level's heights or in a
 * column it does not have, as vanilla's client level answers there.
 */
final class BlockView {
	private final @NotNull JavaWorld world;
	private final @NotNull BlockRegistry registry;
	private final @NotNull BlockState voidAir;
	private final @NotNull Map<CollisionShape, BlockShape> shapes = new ConcurrentHashMap<>();

	BlockView(@NotNull JavaWorld world, @NotNull BlockRegistry registry) {
		this.world = world;
		this.registry = registry;
		this.voidAir = registry.state(registry.block("minecraft:void_air").orElseThrow().getDefaultStateId());
	}

	@NotNull BlockState state(int x, int y, int z) {
		OptionalInt state = world.blockState(x, y, z);
		if (state.isEmpty()) return voidAir;

		return registry.state(state.getAsInt());
	}

	@NotNull BlockShape shape(@NotNull BlockState state) {
		return shapes.computeIfAbsent(state.getCollision(), BlockShape::of);
	}

	/**
	 * Tells whether the client has the chunk column holding a block column.
	 */
	boolean hasChunkAt(int blockX, int blockZ) {
		return world.isLoaded(new ChunkPosition(blockX >> 4, blockZ >> 4));
	}

	/**
	 * Tells whether the client has every chunk column a range of block columns touches, as {@code hasChunksAt} does.
	 */
	boolean hasChunksAt(int fromX, int fromZ, int toX, int toZ) {
		for (int chunkX = fromX >> 4; chunkX <= toX >> 4; chunkX++)
			for (int chunkZ = fromZ >> 4; chunkZ <= toZ >> 4; chunkZ++)
				if (!world.isLoaded(new ChunkPosition(chunkX, chunkZ))) return false;

		return true;
	}

	/**
	 * The lowest height of the level the player is in, or {@code 0} before it knows one.
	 */
	int minY() {
		return world.dimension().map(Dimension::getMinY).orElse(0);
	}

	/**
	 * Tells whether the player is in the Nether, whose lava pushes entities faster.
	 */
	boolean fastLava() {
		return world.dimension().map(dimension -> dimension.getKey().equals("minecraft:the_nether")).orElse(false);
	}
}
