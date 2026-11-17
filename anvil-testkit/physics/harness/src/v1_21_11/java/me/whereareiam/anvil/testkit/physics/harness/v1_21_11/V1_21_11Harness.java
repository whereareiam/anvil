package me.whereareiam.anvil.testkit.physics.harness.v1_21_11;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.CourseKey;
import me.whereareiam.anvil.testkit.physics.harness.Unconstructed;
import me.whereareiam.anvil.testkit.physics.harness.VanillaClient;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import me.whereareiam.anvil.testkit.physics.harness.block.BlockProbe;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.multiplayer.ClientDebugSubscriber;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.PacketProcessor;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.scores.Scoreboard;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The vanilla client of Minecraft 1.21.11, whose registries load from the data pack with the static registries' tags
 * applied first, as the server's world loader loads them since 1.21.2, whose input is the {@code Input} record of the
 * keys a {@code ClientInput} holds, which names identifiers {@code Identifier}, and which ticks the player only once
 * its listener has seen the client load the level.
 */
public final class V1_21_11Harness implements VanillaClient {
	private static final GameProfile PROFILE = new GameProfile(new UUID(0L, 1L), VanillaHarness.PROFILE_NAME);

	private HeadlessMinecraft minecraft;
	private Options options;
	private RegistryAccess.Frozen registries;

	/**
	 * Runs the outputs on the client this JVM's class path holds.
	 *
	 * @param arguments see {@link VanillaHarness#run(String[], VanillaClient)}
	 */
	public static void main(String[] arguments) throws Exception {
		VanillaHarness.run(arguments, new V1_21_11Harness());
	}

	@Override
	public String bootstrap() throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		loadRegistries();

		minecraft = Unconstructed.allocate(HeadlessMinecraft.class);
		Minecraft.instance = minecraft;
		minecraft.gameThread = Thread.currentThread();
		minecraft.packetProcessor = new PacketProcessor(Thread.currentThread());
		File work = Files.createTempDirectory("anvil-physics").toFile();
		work.deleteOnExit();
		minecraft.gameDirectory = work;
		options = new Options(minecraft, work);
		minecraft.options = options;
		// What an offline account's user properties are: telemetry and the player's chat abilities derive from them.
		minecraft.userPropertiesFuture = CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES);
		minecraft.tutorial = Unconstructed.allocate(Tutorial.class);
		minecraft.quickPlayLog = QuickPlayLog.of(null);
		minecraft.debugEntries = new DebugScreenEntryList(work);
		presentation();

		return SharedConstants.getCurrentVersion().name();
	}

	/**
	 * Gives the client what it presents through without presenting anything: sounds, particles, the level's renderer
	 * and the overlay.
	 */
	private void presentation() {
		// Sounds reach a sound engine that never loaded, as on a machine without audio: nothing plays.
		SoundManager sounds = Unconstructed.allocate(SoundManager.class);
		sounds.soundEngine = Unconstructed.allocate(SoundEngine.class);
		minecraft.soundManager = sounds;

		// Particles reach a renderer whose camera was never set up, which drops them; no screen or overlay is drawn.
		GameRenderer renderer = Unconstructed.allocate(GameRenderer.class);
		renderer.mainCamera = new Camera();
		minecraft.gameRenderer = renderer;
		HeadlessLevelRenderer levelRenderer = Unconstructed.allocate(HeadlessLevelRenderer.class);
		levelRenderer.debugRenderer = new DebugRenderer();
		minecraft.levelRenderer = levelRenderer;
		Gui gui = Unconstructed.allocate(Gui.class);
		DebugScreenOverlay overlay = Unconstructed.allocate(DebugScreenOverlay.class);
		overlay.minecraft = minecraft;
		gui.debugOverlay = overlay;
		ChatComponent chat = Unconstructed.allocate(ChatComponent.class);
		chat.minecraft = minecraft;
		gui.chat = chat;
		minecraft.gui = gui;
	}

	/**
	 * Loads the registries of the vanilla data pack as the server's world loader does: the static registries' tags
	 * first, then the worldgen registries against them, then the tags bound.
	 */
	private void loadRegistries() {
		MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource()));
		LayeredRegistryAccess<RegistryLayer> loading = RegistryLayer.createRegistryAccess();
		List<Registry.PendingTags<?>> tags = TagLoader.loadTagsForExistingRegistries(resources, loading.getLayer(RegistryLayer.STATIC));
		List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(loading.getAccessForLoading(RegistryLayer.WORLDGEN), tags);
		RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
		tags.forEach(Registry.PendingTags::apply);
		registries = loading.replaceFrom(RegistryLayer.WORLDGEN, worldgen).compositeAccess();
	}

	@Override
	public CourseRun start(Course course) {
		minecraft.player = null;
		minecraft.level = null;
		minecraft.screen = null;
		RecordingConnection connection = new RecordingConnection();
		ClientPacketListener listener = listener(connection);
		if (course.getJoin() == null) {
			V1_21_11Placement.place(minecraft, listener, course, registries);
			return new V1_21_11Run(minecraft, connection, keys(), List.of());
		}

		// The server's own level, which holds the course's blocks it sends as chunks.
		ClientLevel server = V1_21_11Levels.loaded(listener(new RecordingConnection()), registries, minecraft.renderer(), course.chunkRadius());
		V1_21_11Levels.lay(server, course);
		V1_21_11Placement.join(minecraft, listener, course, registries);
		return new V1_21_11Run(minecraft, connection, keys(), V1_21_11Server.chunks(server, course.chunkRadius()));
	}

	/**
	 * A listener of a connection whose login the server has not sent yet, with what its constructor would give it.
	 */
	private ClientPacketListener listener(RecordingConnection connection) {
		ClientPacketListener listener = Unconstructed.allocate(ClientPacketListener.class);
		listener.minecraft = minecraft;
		listener.connection = connection;
		listener.localGameProfile = PROFILE;
		listener.playerInfoMap = new HashMap<>();
		listener.registryAccess = registries;
		listener.scoreboard = new Scoreboard();
		listener.deferredPackets = new ArrayList<>();
		listener.telemetryManager = new WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, null, null);
		listener.debugSubscriber = new ClientDebugSubscriber(listener, minecraft.gui.debugOverlay);
		connection.packetListener = listener;
		return listener;
	}

	@Override
	public BlockProbe<?, ?> blocks() {
		ClientPacketListener listener = listener(new RecordingConnection());
		ClientLevel level = V1_21_11Levels.loaded(listener, registries, minecraft.renderer(), 1);
		return new V1_21_11Blocks(level, new ProbePlayer(minecraft, level, listener));
	}

	private Map<CourseKey, KeyMapping> keys() {
		Map<CourseKey, KeyMapping> keys = new EnumMap<>(CourseKey.class);
		keys.put(CourseKey.FORWARD, options.keyUp);
		keys.put(CourseKey.BACKWARD, options.keyDown);
		keys.put(CourseKey.LEFT, options.keyLeft);
		keys.put(CourseKey.RIGHT, options.keyRight);
		keys.put(CourseKey.JUMP, options.keyJump);
		keys.put(CourseKey.SNEAK, options.keyShift);
		keys.put(CourseKey.SPRINT, options.keySprint);
		return keys;
	}
}
