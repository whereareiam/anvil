package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * The level renderer of a headless client of Minecraft 1.19.4, which the harness allocates without its constructor: it
 * draws nothing but compiles sections in each {@link #frame()} as {@link CompiledSections} does.
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
}
