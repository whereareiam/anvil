package me.whereareiam.anvil.testkit.physics.harness.v26;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The game of a headless client, which the harness allocates without its constructor: it keeps the game state the
 * handlers set, but runs no frame when a screen shows, since nothing is drawn. Changing the level hands it to its
 * {@link HeadlessRendering} alone, as no sound, particle or title engine runs.
 */
final class HeadlessMinecraft extends Minecraft {
	HeadlessRendering rendering;

	private HeadlessMinecraft() {
		super(null);
	}

	@Override
	public void setLevel(ClientLevel level) {
		this.level = level;
		this.setCameraEntity(null);
		rendering.show(level);
	}

	@Override
	public void setScreenAndShow(Screen screen) {
		V26Client.show(this, screen);
	}
}
