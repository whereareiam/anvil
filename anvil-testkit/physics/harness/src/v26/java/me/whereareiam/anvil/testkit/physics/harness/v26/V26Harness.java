package me.whereareiam.anvil.testkit.physics.harness.v26;

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
import net.minecraft.client.ClientClockManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.multiplayer.ClientDebugSubscriber;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.PacketProcessor;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.datafix.DataFixers;
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
 * The vanilla client of Minecraft 26.1.2 to 26.3, which ships unobfuscated, loads its worldgen registries on an
 * executor, keeps its clocks in the listener's {@code ClientClockManager} and finds its camera entity through the game
 * renderer's camera.
 *
 * <p>The three releases name the same members, except where noted: the level takes a {@code LevelRenderer} in
 * 26.1.2 and a {@code LevelExtractor} from 26.2; from 26.3 the game mode creates a player with the
 * {@code ItemActivation} it shows, the world's registry layer is {@code WORLD} rather than {@code WORLDGEN}, and the
 * vanilla pack is a set of resource layers. Each release's {@link V26Release}, in its own {@code release-<minecraft>}
 * sources, reaches those members.
 */
public final class V26Harness implements VanillaClient {
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
		VanillaHarness.run(arguments, new V26Harness());
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
		minecraft.debugEntries = new DebugScreenEntryList(work, DataFixers.getDataFixer());

		// Sounds reach a sound engine that never loaded, as on a machine without audio: nothing plays.
		SoundManager sounds = Unconstructed.allocate(SoundManager.class);
		sounds.soundEngine = Unconstructed.allocate(SoundEngine.class);
		minecraft.soundManager = sounds;

		// Particles reach a renderer whose camera was never set up, which drops them; no screen or overlay is drawn.
		GameRenderer renderer = Unconstructed.allocate(GameRenderer.class);
		renderer.mainCamera = new Camera();
		minecraft.gameRenderer = renderer;
		minecraft.rendering = V26Client.rendering(minecraft);
		DebugScreenOverlay overlay = Unconstructed.allocate(DebugScreenOverlay.class);
		overlay.minecraft = minecraft;
		ChatComponent chat = Unconstructed.allocate(ChatComponent.class);
		chat.minecraft = minecraft;
		V26Client.gui(minecraft, overlay, chat);

		return SharedConstants.getCurrentVersion().name();
	}

	/**
	 * Loads the registries of the vanilla data pack as the server's world loader does: the static registries' tags
	 * first, then the world registries against them, the dimension types among them, then the tags bound, and the
	 * items' components, which the server binds once its registries are loaded.
	 */
	private void loadRegistries() {
		ResourceManager resources = V26Release.vanillaResources();
		RegistryLayer world = V26Release.worldLayer();
		LayeredRegistryAccess<RegistryLayer> loading = RegistryLayer.createRegistryAccess();
		List<Registry.PendingTags<?>> tags = TagLoader.loadTagsForExistingRegistries(resources, loading.getLayer(RegistryLayer.STATIC));
		List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(loading.getAccessForLoading(world), tags);
		RegistryAccess.Frozen loaded = RegistryDataLoader.load(resources, lookups, V26Release.worldRegistries(), Runnable::run).join();
		tags.forEach(Registry.PendingTags::apply);
		registries = loading.replaceFrom(world, loaded).compositeAccess();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(DataComponentInitializers.PendingComponents::apply);
	}

	@Override
	public CourseRun start(Course course) {
		minecraft.player = null;
		minecraft.level = null;
		V26Client.show(minecraft, null);
		RecordingConnection connection = new RecordingConnection();
		ClientPacketListener listener = listener(connection);
		if (course.getJoin() == null) {
			V26Placement.place(minecraft, listener, course, registries);
			return new V26Run(minecraft, connection, keys(), List.of());
		}

		// The server's own level, which holds the course's blocks it sends as chunks.
		ClientLevel server = V26Levels.loaded(listener(new RecordingConnection()), registries, minecraft.rendering, course.chunkRadius());
		V26Levels.lay(server, course);
		V26Placement.join(minecraft, listener, course, registries);
		return new V26Run(minecraft, connection, keys(), V26Server.chunks(server, course.chunkRadius()));
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
		listener.telemetryManager = V26Client.telemetry();
		listener.debugSubscriber = new ClientDebugSubscriber(listener, minecraft.getDebugOverlay());
		listener.clockManager = new ClientClockManager();
		connection.packetListener = listener;
		return listener;
	}

	@Override
	public BlockProbe<?, ?> blocks() {
		ClientPacketListener listener = listener(new RecordingConnection());
		ClientLevel level = V26Levels.loaded(listener, registries, minecraft.rendering, 1);
		return new V26Blocks(level, new V26Release.ProbePlayer(minecraft, level, listener));
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
