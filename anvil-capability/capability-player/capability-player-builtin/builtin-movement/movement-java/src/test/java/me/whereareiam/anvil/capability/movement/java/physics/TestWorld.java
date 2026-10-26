package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.data.BlockRegistry;
import me.whereareiam.anvil.protocol.java.data.JavaBlockData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A world a test builds block by block with the real block data of one release, whose loaded columns are those the
 * test names; every other block of a loaded column is air.
 */
final class TestWorld implements JavaWorld {
	private final @NotNull BlockRegistry registry;
	private final @NotNull Map<Long, Integer> blocks = new HashMap<>();
	private final @NotNull Set<ChunkPosition> loaded = new HashSet<>();
	private final @NotNull Dimension dimension = new Dimension("minecraft:overworld", -64, 384);
	private boolean inWorld = true;

	TestWorld(@NotNull String version) {
		this.registry = JavaBlockData.shipped().require(MinecraftVersion.parse(version));
	}

	@NotNull BlockRegistry registry() {
		return registry;
	}

	@NotNull TestWorld load(int chunkX, int chunkZ) {
		loaded.add(new ChunkPosition(chunkX, chunkZ));
		return this;
	}

	@NotNull TestWorld unload(int chunkX, int chunkZ) {
		loaded.remove(new ChunkPosition(chunkX, chunkZ));
		return this;
	}

	@NotNull TestWorld set(int x, int y, int z, @NotNull String block) {
		return state(x, y, z, registry.block(block).orElseThrow().getDefaultStateId());
	}

	@NotNull TestWorld state(int x, int y, int z, int state) {
		blocks.put(key(x, y, z), state);
		return this;
	}

	/**
	 * Fills a horizontal layer of blocks.
	 */
	@NotNull TestWorld floor(int fromX, int toX, int y, int fromZ, int toZ, @NotNull String block) {
		for (int x = fromX; x <= toX; x++)
			for (int z = fromZ; z <= toZ; z++)
				set(x, y, z, block);
		return this;
	}

	void leave() {
		inWorld = false;
	}

	@Override
	public @NotNull Optional<Dimension> dimension() {
		return inWorld ? Optional.of(dimension) : Optional.empty();
	}

	@Override
	public boolean isLoaded(@NotNull ChunkPosition chunk) {
		return loaded.contains(chunk);
	}

	@Override
	public @NotNull Set<ChunkPosition> loadedChunks() {
		return Set.copyOf(loaded);
	}

	@Override
	public @NotNull OptionalInt blockState(int x, int y, int z) {
		if (!loaded.contains(new ChunkPosition(x >> 4, z >> 4)) || !dimension.contains(y)) return OptionalInt.empty();

		return OptionalInt.of(blocks.getOrDefault(key(x, y, z), 0));
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
	}
}
