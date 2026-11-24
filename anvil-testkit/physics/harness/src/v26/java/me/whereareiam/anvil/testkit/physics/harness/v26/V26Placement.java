package me.whereareiam.anvil.testkit.physics.harness.v26;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;


/**
 * Brings the player of a course into its level: placed, as a login leaves a player that has loaded its level, or
 * joined, through the client's own handlers of the server's login, first position packet and level loading.
 */
final class V26Placement {
	private V26Placement() {
	}

	/**
	 * Places the player at the course's start, at rest, in a level holding the course's blocks: the game mode creates
	 * the player, the login applies its game mode's abilities and gives it keyboard input, the level adds it, the
	 * position packet places it at rest, and the client has loaded the level.
	 */
	static void place(HeadlessMinecraft minecraft, ClientPacketListener listener, Course course, RegistryAccess registries) {
		MultiPlayerGameMode gameMode = new MultiPlayerGameMode(minecraft, listener);
		minecraft.gameMode = gameMode;
		ClientLevel level = V26Levels.loaded(listener, registries, minecraft.rendering, course.chunkRadius());
		V26Levels.lay(level, course);
		minecraft.setLevel(level);

		LocalPlayer player = V26Release.createPlayer(gameMode, level);
		player.setId(VanillaHarness.ENTITY_ID);
		minecraft.player = player;
		gameMode.setLocalMode(GameType.SURVIVAL, null);
		player.input = new KeyboardInput(minecraft.options);
		minecraft.setCameraEntity(player);
		level.addEntity(player);
		Course.Start start = course.getStart();
		player.snapTo(start.getX(), start.getY(), start.getZ(), start.getYaw(), start.getPitch());
		player.setDeltaMovement(Vec3.ZERO);
		listener.clientLoaded = true;
		seed(player, level);
	}

	/**
	 * Joins the player as over the network: the login, the first position packet and the game event that lets the
	 * client wait for the level's chunks, each through the client's own handler.
	 */
	static void join(HeadlessMinecraft minecraft, ClientPacketListener listener, Course course, RegistryAccess registries) {
		listener.handleLogin(V26Server.login(registries));
		seed(minecraft.player, minecraft.level);
		for (Course.Ping ping : course.handshake())
			listener.handlePing(V26Server.ping(ping));
		listener.handleMovePlayer(V26Server.position(course.getJoin().teleport()));
		listener.handleGameEvent(V26Server.levelChunksLoadStart());
	}

	private static void seed(LocalPlayer player, ClientLevel level) {
		player.getRandom().setSeed(VanillaHarness.SEED);
		level.getRandom().setSeed(VanillaHarness.SEED);
	}
}
