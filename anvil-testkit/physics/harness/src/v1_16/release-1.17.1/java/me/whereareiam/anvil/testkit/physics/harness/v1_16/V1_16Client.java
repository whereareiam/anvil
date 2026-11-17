package me.whereareiam.anvil.testkit.physics.harness.v1_16;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.CourseRelative;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import net.minecraft.client.Game;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.searchtree.ReloadableIdSearchTree;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What the headless vanilla client of Minecraft 1.17.1 sets up and receives differently from the other releases of its
 * family, compiled against that client alone: its login names the dimension type itself, its chunk packet carries no light, and its position packet says whether the player dismounts.
 */
final class V1_16Client {
	private V1_16Client() {
	}

	/**
	 * Gives the game what only its handlers reach: the game session the login starts, and the creative search tree the
	 * tags packet refreshes, empty.
	 */
	static void setup(HeadlessMinecraft minecraft) {
		minecraft.game = new Game(minecraft);
		SearchRegistry search = new SearchRegistry();
		search.register(SearchRegistry.CREATIVE_TAGS, new ReloadableIdSearchTree<>(stack -> Stream.empty()));
		minecraft.searchRegistry = search;
	}

	/**
	 * Gives a listener what its constructor would: the built-in registries.
	 */
	static void listener(ClientPacketListener listener, RegistryAccess registries) {
		listener.registryAccess = registries;
	}

	/**
	 * Tells the level that a chunk loaded, as the chunk packet's handler does.
	 */
	static void chunkLoaded(ClientLevel level, int x, int z) {
		level.onChunkLoaded(new ChunkPos(x, z));
	}

	/**
	 * Puts the player into survival, as the login does.
	 */
	static void survival(MultiPlayerGameMode gameMode) {
		gameMode.setLocalMode(GameType.SURVIVAL, null);
	}

	/**
	 * Logs the player in through the listener's own handler: a fresh survival player of the overworld.
	 */
	static void login(ClientPacketListener listener, RegistryAccess registries, int viewDistance, int maxPlayers) {
		listener.handleLogin(new ClientboundLoginPacket(VanillaHarness.ENTITY_ID, GameType.SURVIVAL, null, 0L, false,
			Set.of(Level.OVERWORLD), (RegistryAccess.RegistryHolder) registries,
			registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).get(DimensionType.OVERWORLD_LOCATION), Level.OVERWORLD, maxPlayers, viewDistance, false, true, false,
			false));
	}

	static ClientboundPlayerPositionPacket position(Course.Teleport teleport) {
		Set<ClientboundPlayerPositionPacket.RelativeArgument> relatives = EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class);
		for (CourseRelative relative : teleport.getRelatives())
			relatives.add(ClientboundPlayerPositionPacket.RelativeArgument.valueOf(relative(relative)));
		return new ClientboundPlayerPositionPacket(teleport.getX(), teleport.getY(), teleport.getZ(), teleport.getYaw(), teleport.getPitch(),
			relatives, teleport.getId(), false);
	}

	/**
	 * The deliveries of the chunk packets of every loaded column of the server's level within {@code radius} chunks of
	 * the origin, each through the listener's own handler.
	 */
	static List<Runnable> chunks(ClientPacketListener listener, ClientLevel server, int radius) {
		List<Runnable> deliveries = new ArrayList<>();
		for (int x = -radius; x <= radius; x++)
			for (int z = -radius; z <= radius; z++) {
				ClientboundLevelChunkPacket packet = new ClientboundLevelChunkPacket(server.getChunk(x, z));
				deliveries.add(() -> listener.handleLevelChunk(packet));
			}
		return deliveries;
	}

	private static String relative(CourseRelative relative) {
		switch (relative) {
			case X:
			case Y:
			case Z:
				return relative.name();
			case YAW:
				return "Y_ROT";
			case PITCH:
				return "X_ROT";
			default:
				throw new IllegalArgumentException("A position packet before Minecraft 1.21.2 carries no " + relative);
		}
	}
}
