package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The game of a headless client of Minecraft 1.21.1, which the harness allocates without its constructor: it keeps the
 * game state the handlers set, but shows no screen and runs no frame when the level changes, since nothing is drawn.
 * Changing the level hands it to the {@link HeadlessLevelRenderer} alone, as no sound, particle or title engine runs.
 */
final class HeadlessMinecraft extends Minecraft {
	private HeadlessMinecraft() {
		super(null);
	}

	/**
	 * The level renderer, which the harness gives the game.
	 */
	HeadlessLevelRenderer renderer() {
		return (HeadlessLevelRenderer) levelRenderer;
	}

	@Override
	public void setLevel(ClientLevel level, ReceivingLevelScreen.Reason reason) {
		this.level = level;
		this.cameraEntity = null;
		renderer().show(level);
	}

	@Override
	public void setScreen(Screen screen) {
		this.screen = screen;
	}
}
