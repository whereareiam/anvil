package me.whereareiam.anvil.testkit.physics.harness.v1_21_11;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;

/**
 * The level renderer of a headless client, which the harness allocates without its constructor. It draws nothing, but
 * renders a {@link #frame()} after every client tick, as {@code Minecraft.runTick} renders after its ticks, and
 * compiles in it every section it could: a section whose chunk and the eight around it are loaded, as
 * {@code RenderSection.hasAllNeighbors} requires before a section compiles. A section compiled in a frame counts as
 * compiled and visible from then on; the vanilla client compiles it on worker threads and fades it in over the next
 * frames, whose timing a headless client has none of.
 */
final class HeadlessLevelRenderer extends LevelRenderer {
	private ClientLevel shown;
	private Set<ChunkPos> compiled;

	private HeadlessLevelRenderer() {
		super(null, null, null, null, null, null);
	}

	@Override
	public void setLevel(ClientLevel level) {
		this.shown = level;
		this.compiled = new HashSet<>();
	}

	/**
	 * Renders a frame: compiles the sections of every loaded chunk whose eight neighbours are loaded too.
	 */
	void frame() {
		if (shown == null)
			return;
		int radius = shown.getChunkSource().storage.chunkRadius;
		for (int x = -radius; x <= radius; x++)
			for (int z = -radius; z <= radius; z++)
				if (surrounded(x, z))
					compiled.add(new ChunkPos(x, z));
	}

	private boolean surrounded(int chunkX, int chunkZ) {
		for (int x = -1; x <= 1; x++)
			for (int z = -1; z <= 1; z++)
				if (shown.getChunkSource().getChunk(chunkX + x, chunkZ + z, false) == null)
					return false;
		return true;
	}

	@Override
	public boolean isSectionCompiledAndVisible(BlockPos position) {
		return compiled.contains(new ChunkPos(position));
	}
}
