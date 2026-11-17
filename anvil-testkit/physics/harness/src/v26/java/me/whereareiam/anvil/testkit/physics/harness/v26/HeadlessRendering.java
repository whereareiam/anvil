package me.whereareiam.anvil.testkit.physics.harness.v26;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * What draws the level in a headless client of the family: the level renderer of 26.1.2, the level extractor from
 * 26.2, each a stand-in of its release that draws nothing but compiles sections as {@link CompiledSections} does.
 */
interface HeadlessRendering {
	/**
	 * Shows a level, as changing the game's level does.
	 *
	 * @param level the level
	 */
	void show(ClientLevel level);

	/**
	 * Renders the frame that {@code Minecraft.runTick} renders after its ticks.
	 */
	void frame();
}
