package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import com.mojang.authlib.minecraft.UserApiService;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.DebugSampleSubscriber;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * What the headless vanilla client of Minecraft 1.20.6 sets up and receives differently from the other releases of its
 * family, compiled against that client alone: the configuration brought its registries and tags before the login,
 * whose spawn information is a record of its own, its listener's scoreboard is its own, and the server sends the event
 * that starts the level's loading.
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
	 * Gives the game what only this release's handlers reach: an offline account's user properties and a quick play log
	 * that logs nothing.
	 */
	static void setup(HeadlessMinecraft minecraft) {
		minecraft.userPropertiesFuture = CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES);
		minecraft.quickPlayLog = QuickPlayLog.of(null);
	}

	/**
	 * Gives the GUI the overlay and chat the handlers reach.
	 */
	static void gui(Gui gui, DebugScreenOverlay overlay, ChatComponent chat) {
		gui.debugOverlay = overlay;
		gui.chat = chat;
	}

	/**
	 * Gives a listener what its constructor would: the registries the configuration brought, the empty scoreboard the
	 * login creates, the deferred packets, a world session's telemetry that sends nothing and the debug samples it
	 * subscribes to.
	 */
	static void listener(ClientPacketListener listener, LayeredRegistryAccess<RegistryLayer> layers, RegistryAccess.Frozen registries) {
		listener.registryAccess = registries;
		listener.scoreboard = new Scoreboard();
		listener.deferredPackets = new ArrayList<>();
		listener.telemetryManager = new WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, null, null);
		listener.debugSampleSubscriber = new DebugSampleSubscriber(listener, listener.minecraft.gui.debugOverlay);
	}

	/**
	 * Logs the player in through the listener's own handler, with the registries the configuration brought.
	 */
	static void login(ClientPacketListener listener, LayeredRegistryAccess<RegistryLayer> layers, int viewDistance, int maxPlayers) {
		CommonPlayerSpawnInfo spawn = new CommonPlayerSpawnInfo(V1_19Levels.overworld(layers.compositeAccess()), Level.OVERWORLD, 0L,
			GameType.SURVIVAL, null, false, false, Optional.empty(), 0);
		listener.handleLogin(new ClientboundLoginPacket(VanillaHarness.ENTITY_ID, false, Set.of(Level.OVERWORLD), maxPlayers, viewDistance,
			viewDistance, false, true, false, spawn, false));
	}

	/**
	 * Sends the event that lets the client wait for the level's chunks, as a Paper server does after it placed the
	 * player.
	 */
	static void levelChunksLoadStart(ClientPacketListener listener) {
		listener.handleGameEvent(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
	}

	static ClientboundLevelChunkWithLightPacket chunk(LevelChunk chunk, LevelLightEngine light) {
		return new ClientboundLevelChunkWithLightPacket(chunk, light, null, null);
	}
}
