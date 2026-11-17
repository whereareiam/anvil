package me.whereareiam.anvil.testkit.physics.harness.v1_16;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * The level renderer of a headless client, which the harness allocates without its constructor: it draws nothing but
 * compiles sections in each {@link #frame()} as {@link CompiledSections} does.
 */
final class HeadlessLevelRenderer extends LevelRenderer {
	CompiledSections sections;

	private HeadlessLevelRenderer() {
		super(null, null);
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
	 * Marks the sections around one to compile again, which a chunk packet's handler does; the headless renderer
	 * compiles from the level's chunks in each frame instead.
	 */
	@Override
	public void setSectionDirtyWithNeighbors(int x, int y, int z) {
		// The headless renderer keeps no sections to mark.
	}

	/**
	 * Renders the frame that {@code Minecraft.runTick} renders after its ticks.
	 */
	void frame() {
		sections.frame();
	}
}
