package me.whereareiam.anvil.testkit.physics.harness.v1_16;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.phys.Vec3;


/**
 * Brings the player of a course into its level: placed, as a login leaves a player whose level's chunks are loaded, or
 * joined, through the client's own handlers of the server's login, tags and first position packet.
 */
final class V1_16Placement {
	private V1_16Placement() {
	}

	/**
	 * Places the player at the course's start, at rest, in a level holding the course's blocks: the game mode creates
	 * the player, the login applies its game mode's abilities and gives it keyboard input, the level adds it, and the
	 * position packet places it at rest.
	 */
	static void place(HeadlessMinecraft minecraft, ClientPacketListener listener, Course course, RegistryAccess registries) {
		MultiPlayerGameMode gameMode = new MultiPlayerGameMode(minecraft, listener);
		minecraft.gameMode = gameMode;
		ClientLevel level = V1_16Levels.loaded(listener, registries, minecraft.renderer(), course.chunkRadius());
		V1_16Levels.lay(level, course);
		minecraft.level = level;
		minecraft.renderer().show(level);

		LocalPlayer player = gameMode.createPlayer(level, new StatsCounter(), new ClientRecipeBook());
		player.setId(VanillaHarness.ENTITY_ID);
		minecraft.player = player;
		V1_16Client.survival(gameMode);
		player.input = new KeyboardInput(minecraft.options);
		minecraft.cameraEntity = player;
		level.addPlayer(player.getId(), player);
		Course.Start start = course.getStart();
		player.moveTo(start.getX(), start.getY(), start.getZ(), start.getYaw(), start.getPitch());
		player.setDeltaMovement(Vec3.ZERO);
		seed(player, level);
	}

	/**
	 * Joins the player as over the network: the login, the tags Paper sends after it and the first position packet, each
	 * through the client's own handler.
	 */
	static void join(HeadlessMinecraft minecraft, ClientPacketListener listener, Course course, RegistryAccess registries,
		ClientboundUpdateTagsPacket tags) {
		V1_16Client.login(listener, registries, V1_16Server.VIEW_DISTANCE, V1_16Server.MAX_PLAYERS);
		listener.handleUpdateTags(tags);
		seed(minecraft.player, minecraft.level);
		for (Course.Ping ping : course.handshake())
			V1_16Client.ping(listener, ping);
		listener.handleMovePlayer(V1_16Client.position(course.getJoin().teleport()));
	}

	private static void seed(LocalPlayer player, ClientLevel level) {
		player.getRandom().setSeed(VanillaHarness.SEED);
		level.random.setSeed(VanillaHarness.SEED);
	}
}
