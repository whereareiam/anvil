package me.whereareiam.anvil.testkit.physics.harness.v26;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.extract.LevelExtractor;

/**
 * The level extractor of a headless client of Minecraft 26.2, which the harness allocates without its constructor: it
 * draws nothing but compiles sections in each {@link #frame()} as {@link CompiledSections} does, and when the section
 * the camera is in, at the player, has compiled, it tells the level's loading through the callback the listener hands a
 * frame ({@code getPlayerCompiledSectionCallback}), as the renderer does once it compiled that section.
 */
final class HeadlessLevelExtractor extends LevelExtractor implements HeadlessRendering {
	CompiledSections sections;

	private HeadlessLevelExtractor() {
		super(null, null, null);
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
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener connection = minecraft.getConnection();
		Runnable compiled = connection == null ? null : connection.getPlayerCompiledSectionCallback();
		if (compiled != null && sections.compiled(minecraft.player.blockPosition()))
			compiled.run();
	}
}
