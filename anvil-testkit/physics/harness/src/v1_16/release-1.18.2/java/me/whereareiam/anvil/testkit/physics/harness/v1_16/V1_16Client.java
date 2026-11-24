package me.whereareiam.anvil.testkit.physics.harness.v1_16;

import com.mojang.authlib.minecraft.UserApiService;
import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.CourseRelative;
import me.whereareiam.anvil.testkit.physics.harness.PacketLine;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import net.minecraft.client.ClientTelemetryManager;
import net.minecraft.client.Game;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.searchtree.ReloadableIdSearchTree;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * What the headless vanilla client of Minecraft 1.18.2 sets up and receives differently from the other releases of its
 * family, compiled against that client alone: its login names the dimension type's holder and the simulation distance, its chunk packet carries the light, its position packet says whether the player dismounts, and its listener keeps the session's telemetry.
 */
final class V1_16Client {
	private V1_16Client() {
	}

	/**
	 * Gives the game what only its handlers reach: the game session the login starts, and the creative search tree the
	 * tags packet refreshes, empty, and the user API service of an offline account, which the options it broadcasts ask.
	 */
	static void setup(HeadlessMinecraft minecraft) {
		minecraft.game = new Game(minecraft);
		SearchRegistry search = new SearchRegistry();
		search.register(SearchRegistry.CREATIVE_TAGS, new ReloadableIdSearchTree<>(stack -> Stream.empty()));
		minecraft.searchRegistry = search;
		minecraft.userApiService = UserApiService.OFFLINE;
	}

	/**
	 * Gives a listener what its constructor would: the built-in registries, frozen, and the telemetry of an offline
	 * account, which sends nothing.
	 */
	static void listener(ClientPacketListener listener, RegistryAccess registries) {
		listener.registryAccess = (RegistryAccess.Frozen) registries;
		listener.telemetryManager = new ClientTelemetryManager(listener.minecraft, UserApiService.OFFLINE, Optional.empty(), Optional.empty(), new UUID(0L, 0L));
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
		listener.handleLogin(new ClientboundLoginPacket(VanillaHarness.ENTITY_ID, false, GameType.SURVIVAL, null, Set.of(Level.OVERWORLD),
			(RegistryAccess.Frozen) registries, registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getHolderOrThrow(DimensionType.OVERWORLD_LOCATION),
			Level.OVERWORLD, 0L, maxPlayers, viewDistance, viewDistance, false, true, false, false));
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
				ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(server.getChunk(x, z), server.getLightEngine(), null, null, true);
				deliveries.add(() -> listener.handleLevelChunkWithLight(packet));
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

	/**
	 * Hands the client a ping of the server through the listener's own handler.
	 */
	static void ping(ClientPacketListener listener, Course.Ping ping) {
		listener.handlePing(new ClientboundPingPacket(ping.getId()));
	}

	/**
	 * Returns the line of the client's answer to a ping, its pong, or nothing for another packet.
	 */
	static Optional<PacketLine> answer(Packet<?> packet) {
		if (!(packet instanceof ServerboundPongPacket))
			return Optional.empty();
		return Optional.of(PacketLine.of("ServerboundPong").with("id", ((ServerboundPongPacket) packet).getId()));
	}
}
