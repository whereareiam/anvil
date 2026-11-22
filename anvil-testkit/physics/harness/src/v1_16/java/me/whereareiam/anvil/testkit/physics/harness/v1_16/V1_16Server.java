package me.whereareiam.anvil.testkit.physics.harness.v1_16;

/**
 * What a Paper server's defaults give the vanilla clients of Minecraft 1.16.5 to 1.18.2 that join; {@link V1_16Client}
 * creates each release's packets.
 */
final class V1_16Server {
	/**
	 * The view and simulation distance of a Paper server's defaults.
	 */
	static final int VIEW_DISTANCE = 10;
	static final int MAX_PLAYERS = 20;

	private V1_16Server() {
	}
}
