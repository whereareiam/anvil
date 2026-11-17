package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * The level renderer of a headless client of Minecraft 1.21.1, which the harness allocates without its constructor: it
 * draws nothing but compiles sections in each {@link #frame()} as {@link CompiledSections} does, and the level's loading screen asks it whether the player's section is compiled.
 */
final class HeadlessLevelRenderer extends LevelRenderer {
	CompiledSections sections;

	private HeadlessLevelRenderer() {
		super(null, null, null, null);
	}

	/**
	 * Shows a level, as changing the game's level does.
	 */
	void show(ClientLevel level) {
		sections.show(level);
	}

	@Override
	public void setLevel(ClientLevel level) {
		show(level);
	}

	/**
	 * Renders the frame that {@code Minecraft.runTick} renders after its ticks.
	 */
	void frame() {
		sections.frame();
	}

	/**
	 * Takes note of a loaded chunk, which the renderer would hand its section graph; the headless renderer reads the
	 * level's chunks when it compiles instead.
	 */
	@Override
	public void onChunkLoaded(ChunkPos chunk) {
		// The headless renderer has no section graph.
	}

	@Override
	public boolean isSectionCompiled(BlockPos position) {
		return sections.compiled(position);
	}
}
