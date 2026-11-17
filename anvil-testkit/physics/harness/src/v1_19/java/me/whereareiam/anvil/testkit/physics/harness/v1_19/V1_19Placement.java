package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Brings the player of a course into its level: placed, as a login leaves a player whose level's chunks are loaded, or
 * joined, through the client's own handlers of the server's login, first position packet and level loading.
 */
final class V1_19Placement {
	private V1_19Placement() {
	}

	/**
	 * Places the player at the course's start, at rest, in a level holding the course's blocks: the game mode creates
	 * the player, the login applies its game mode's abilities and gives it keyboard input, the level adds it, and the
	 * position packet places it at rest.
	 */
	static void place(HeadlessMinecraft minecraft, ClientPacketListener listener, Course course, RegistryAccess registries) {
		MultiPlayerGameMode gameMode = new MultiPlayerGameMode(minecraft, listener);
		minecraft.gameMode = gameMode;
		ClientLevel level = V1_19Levels.loaded(listener, registries, minecraft.renderer(), course.chunkRadius());
		V1_19Levels.lay(level, course);
		minecraft.level = level;
		minecraft.renderer().show(level);

		LocalPlayer player = gameMode.createPlayer(level, new StatsCounter(), new ClientRecipeBook());
		player.setId(VanillaHarness.ENTITY_ID);
		minecraft.player = player;
		gameMode.setLocalMode(GameType.SURVIVAL, null);
		player.input = new KeyboardInput(minecraft.options);
		minecraft.cameraEntity = player;
		V1_19Release.addPlayer(level, player);
		Course.Start start = course.getStart();
		player.moveTo(start.getX(), start.getY(), start.getZ(), start.getYaw(), start.getPitch());
		player.setDeltaMovement(Vec3.ZERO);
		seed(player, level);
	}

	/**
	 * Joins the player as over the network: the login, the first position packet and, where the release has it, the
	 * game event that lets the client wait for the level's chunks, each through the client's own handler.
	 */
	static void join(HeadlessMinecraft minecraft, ClientPacketListener listener, Course course, LayeredRegistryAccess<RegistryLayer> layers) {
		V1_19Client.login(listener, layers, V1_19Server.VIEW_DISTANCE, V1_19Server.MAX_PLAYERS);
		seed(minecraft.player, minecraft.level);
		Course.Join join = course.getJoin();
		listener.handleMovePlayer(V1_19Server.position(new Course.Teleport(Course.Join.TELEPORT_ID, join.getX(), join.getY(), join.getZ(),
			join.getYaw(), join.getPitch(), 0.0, 0.0, 0.0, Set.of())));
		V1_19Client.levelChunksLoadStart(listener);
	}

	private static void seed(LocalPlayer player, ClientLevel level) {
		player.getRandom().setSeed(VanillaHarness.SEED);
		level.random.setSeed(VanillaHarness.SEED);
		level.threadSafeRandom.setSeed(VanillaHarness.SEED);
	}
}
