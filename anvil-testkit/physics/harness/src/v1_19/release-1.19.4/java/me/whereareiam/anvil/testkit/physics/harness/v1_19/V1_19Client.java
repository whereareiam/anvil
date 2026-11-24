package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import com.mojang.authlib.minecraft.UserApiService;
import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.PacketLine;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.server.RegistryLayer;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

/**
 * What the headless vanilla client of Minecraft 1.19.4 sets up and receives differently from the other releases of its
 * family, compiled against that client alone: its listener keeps the registries as a layered registry access whose
 * remote layer the login brings, so the login carries the synchronized registries and resets their tags, which the
 * server's tags packet then sends; and the server has no event that starts the level's loading.
 */
final class V1_19Client {
	/**
	 * The packets the client sends that movement does not concern: its settings, its brand and its chat session.
	 */
	static final Set<Class<?>> LEFT_OUT = Set.of(ServerboundClientInformationPacket.class, ServerboundCustomPayloadPacket.class,
		ServerboundChatSessionUpdatePacket.class);

	private V1_19Client() {
	}

	/**
	 * Gives the game what only this release's handlers reach: the user API service of an offline account, which the
	 * telemetry asks.
	 */
	static void setup(HeadlessMinecraft minecraft) {
		minecraft.userApiService = UserApiService.OFFLINE;
	}

	/**
	 * Gives the GUI the overlay and chat the handlers reach.
	 */
	static void gui(Gui gui, DebugScreenOverlay overlay, ChatComponent chat) {
		gui.chat = chat;
	}

	/**
	 * Gives a listener the registries a login leaves it, the synchronized registries of the server as the remote layer of
	 * a layered registry access, and what its constructor would: the deferred packets and a world session's telemetry that
	 * sends nothing.
	 */
	static void listener(ClientPacketListener listener, LayeredRegistryAccess<RegistryLayer> layers, RegistryAccess.Frozen registries) {
		listener.registryAccess = ClientRegistryLayer.createRegistryAccess().replaceFrom(ClientRegistryLayer.REMOTE, synchronizedRegistries(layers));
		listener.deferredPackets = new ArrayList<>();
		listener.telemetryManager = new WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, null);
	}

	/**
	 * Logs the player in through the listener's own handlers: the login, which brings the synchronized registries, then
	 * the tags packet Paper sends after it. The server's tags are read before the login, since the client's registries
	 * are the server's own in this JVM and the login resets their tags.
	 */
	static void login(ClientPacketListener listener, LayeredRegistryAccess<RegistryLayer> layers, int viewDistance, int maxPlayers) {
		ClientboundUpdateTagsPacket tags = new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(layers));
		listener.handleLogin(new ClientboundLoginPacket(VanillaHarness.ENTITY_ID, false, GameType.SURVIVAL, null, Set.of(Level.OVERWORLD),
			synchronizedRegistries(layers), BuiltinDimensionTypes.OVERWORLD, Level.OVERWORLD, 0L, maxPlayers, viewDistance, viewDistance, false, true, false,
			false, Optional.empty()));
		listener.handleUpdateTags(tags);
	}

	/**
	 * The registries a server synchronizes with its clients.
	 */
	private static RegistryAccess.Frozen synchronizedRegistries(LayeredRegistryAccess<RegistryLayer> layers) {
		return new RegistryAccess.ImmutableRegistryAccess(RegistrySynchronization.networkedRegistries(layers)).freeze();
	}

	/**
	 * The server of Minecraft 1.19.4 has no event that starts the level's loading; it sends nothing.
	 */
	static void levelChunksLoadStart(ClientPacketListener listener) {
		// Nothing to send.
	}

	static ClientboundLevelChunkWithLightPacket chunk(LevelChunk chunk, LevelLightEngine light) {
		return new ClientboundLevelChunkWithLightPacket(chunk, light, null, null, true);
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
		if (!(packet instanceof ServerboundPongPacket pong))
			return Optional.empty();
		return Optional.of(PacketLine.of("ServerboundPong").with("id", pong.getId()));
	}
}
