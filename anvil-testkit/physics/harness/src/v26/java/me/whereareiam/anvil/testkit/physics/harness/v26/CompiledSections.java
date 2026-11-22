package me.whereareiam.anvil.testkit.physics.harness.v26;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;

/**
 * The sections a headless client's renderer has compiled: in every frame it compiles the sections of each loaded chunk
 * whose eight neighbours are loaded too, as {@code RenderSection.hasAllNeighbors} requires before a section compiles,
 * and a section compiled once counts as compiled and visible from then on. The vanilla client compiles it on worker
 * threads and fades it in over the next frames, whose timing a headless client has none of.
 */
final class CompiledSections {
	private ClientLevel shown;
	private Set<ChunkPos> compiled = new HashSet<>();

	/**
	 * Shows a new level, whose sections none are compiled.
	 */
	void show(ClientLevel level) {
		shown = level;
		compiled = new HashSet<>();
	}

	/**
	 * Compiles every section a frame could.
	 */
	void frame() {
		if (shown == null)
			return;
		ClientChunkCache.Storage storage = shown.getChunkSource().storage;
		for (int x = storage.viewCenterX - storage.chunkRadius; x <= storage.viewCenterX + storage.chunkRadius; x++)
			for (int z = storage.viewCenterZ - storage.chunkRadius; z <= storage.viewCenterZ + storage.chunkRadius; z++)
				if (surrounded(x, z))
					compiled.add(new ChunkPos(x, z));
	}

	/**
	 * Whether the section at a position was compiled.
	 */
	boolean compiled(BlockPos position) {
		return compiled.contains(ChunkPos.containing(position));
	}

	private boolean surrounded(int chunkX, int chunkZ) {
		for (int x = -1; x <= 1; x++)
			for (int z = -1; z <= 1; z++)
				if (shown.getChunkSource().getChunk(chunkX + x, chunkZ + z, false) == null)
					return false;
		return true;
	}
}
