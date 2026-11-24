package me.whereareiam.anvil.testkit.physics.harness.v26;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

/**
 * The level renderer of a headless client of Minecraft 26.1.2, which the harness allocates without its constructor: it
 * draws nothing but compiles sections in each {@link #frame()} as {@link CompiledSections} does, and the level's
 * loading asks it whether the player's section is compiled and visible.
 */
final class HeadlessLevelRenderer extends LevelRenderer implements HeadlessRendering {
	CompiledSections sections;

	private HeadlessLevelRenderer() {
		super(null, null, null, null, null, null);
	}

	@Override
	public void show(ClientLevel level) {
		sections.show(level);
	}

	@Override
	public void setLevel(ClientLevel level) {
		show(level);
	}

	@Override
	public void frame() {
		sections.frame();
	}

	@Override
	public boolean isSectionCompiledAndVisible(BlockPos position) {
		return sections.compiled(position);
	}
}
