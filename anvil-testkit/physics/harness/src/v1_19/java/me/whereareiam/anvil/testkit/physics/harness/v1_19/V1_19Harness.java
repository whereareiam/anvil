package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.level.block.Blocks;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The vanilla client of Minecraft 1.19.4 to 1.21.1, whose registries load from the data pack through the layered
 * registry access as the server's world loader loads them, whose tags bind through the tag manager, and whose input is
 * the boolean {@code Input}.
 *
 * <p>The three releases name the same members, except where noted: the server's vanilla pack source is private in
 * 1.19.4; 1.19.4 keeps the listener's registries as a layered registry access and a connection's unsent packets in
 * {@code queue}, while later releases keep a frozen registry access and the listener's connection in its common
 * superclass; 1.19.4 adds a player to its level through {@code addPlayer}, later releases through {@code addEntity};
 * and the login of 1.19.4 brings the registries, whose tags the server sends after it. Each release's
 * {@link V1_19Release} and {@link V1_19Client}, in its own {@code release-<minecraft>} sources, reach those members.
 */
public final class V1_19Harness implements VanillaClient {
	private static final GameProfile PROFILE = new GameProfile(new UUID(0L, 1L), VanillaHarness.PROFILE_NAME);
	private static final Executor DIRECT = Runnable::run;

	private HeadlessMinecraft minecraft;
	private Options options;
	private LayeredRegistryAccess<RegistryLayer> layers;
	private RegistryAccess.Frozen registries;

	/**
	 * Runs the outputs on the client this JVM's class path holds.
	 *
	 * @param arguments see {@link VanillaHarness#run(String[], VanillaClient)}
	 */
	public static void main(String[] arguments) throws Exception {
		VanillaHarness.run(arguments, new V1_19Harness());
	}

	@Override
	public String bootstrap() throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		loadRegistries();

		minecraft = Unconstructed.allocate(HeadlessMinecraft.class);
		Minecraft.instance = minecraft;
		minecraft.gameThread = Thread.currentThread();
		minecraft.profiler = InactiveProfiler.INSTANCE;
		File work = Files.createTempDirectory("anvil-physics").toFile();
		work.deleteOnExit();
		minecraft.gameDirectory = work;
		options = new Options(minecraft, work);
		minecraft.options = options;
		minecraft.tutorial = Unconstructed.allocate(Tutorial.class);
		V1_19Client.setup(minecraft);

		// Sounds reach a sound engine that never loaded, as on a machine without audio: nothing plays.
		SoundManager sounds = Unconstructed.allocate(SoundManager.class);
		sounds.soundEngine = Unconstructed.allocate(SoundEngine.class);
		minecraft.soundManager = sounds;

		// Particles reach a renderer whose camera was never set up, which drops them; no screen or overlay is drawn.
		GameRenderer renderer = Unconstructed.allocate(GameRenderer.class);
		renderer.mainCamera = new Camera();
		minecraft.gameRenderer = renderer;
		HeadlessLevelRenderer levelRenderer = Unconstructed.allocate(HeadlessLevelRenderer.class);
		levelRenderer.sections = new CompiledSections();
		levelRenderer.minecraft = minecraft;
		minecraft.levelRenderer = levelRenderer;
		minecraft.debugRenderer = new DebugRenderer(minecraft);
		DebugScreenOverlay overlay = Unconstructed.allocate(DebugScreenOverlay.class);
		overlay.minecraft = minecraft;
		ChatComponent chat = Unconstructed.allocate(ChatComponent.class);
		chat.minecraft = minecraft;
		Gui gui = Unconstructed.allocate(Gui.class);
		V1_19Client.gui(gui, overlay, chat);
		minecraft.gui = gui;

		return SharedConstants.getCurrentVersion().getName();
	}

	/**
	 * Loads the registries and tags of the vanilla data pack as the server's world loader does, and binds the tags as
	 * the server binds them before it sends them to the client.
	 */
	private void loadRegistries() throws Exception {
		PackResources vanilla = V1_19Release.vanillaPack();
		MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla));
		LayeredRegistryAccess<RegistryLayer> loading = RegistryLayer.createRegistryAccess();
		RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources, loading.getAccessForLoading(RegistryLayer.WORLDGEN),
			RegistryDataLoader.WORLDGEN_REGISTRIES);
		layers = loading.replaceFrom(RegistryLayer.WORLDGEN, worldgen);
		registries = layers.compositeAccess();

		TagManager tags = new TagManager(registries);
		PreparableReloadListener.PreparationBarrier barrier = new PreparableReloadListener.PreparationBarrier() {
			@Override
			public <T> CompletableFuture<T> wait(T value) {
				return CompletableFuture.completedFuture(value);
			}
		};
		tags.reload(barrier, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, DIRECT, DIRECT).get();
		for (TagManager.LoadResult<?> result : tags.getResult())
			bind(result);
		Blocks.rebuildCache();
	}

	private <T> void bind(TagManager.LoadResult<T> result) {
		ResourceKey<? extends Registry<T>> key = result.key();
		Map<TagKey<T>, List<Holder<T>>> bound = new HashMap<>();
		for (Map.Entry<ResourceLocation, Collection<Holder<T>>> entry : result.tags().entrySet())
			bound.put(TagKey.create(key, entry.getKey()), List.copyOf(entry.getValue()));
		registries.registryOrThrow(key).bindTags(bound);
	}

	@Override
	public CourseRun start(Course course) {
		minecraft.player = null;
		minecraft.level = null;
		minecraft.screen = null;
		RecordingConnection connection = new RecordingConnection();
		ClientPacketListener listener = listener(connection);
		if (course.getJoin() == null) {
			V1_19Placement.place(minecraft, listener, course, registries);
			return new V1_19Run(minecraft, connection, keys(), List.of());
		}

		// The server's own level, which holds the course's blocks it sends as chunks.
		ClientLevel server = V1_19Levels.loaded(listener(new RecordingConnection()), registries, minecraft.renderer(), course.chunkRadius());
		V1_19Levels.lay(server, course);
		V1_19Placement.join(minecraft, listener, course, layers);
		return new V1_19Run(minecraft, connection, keys(), V1_19Server.chunks(server, course.chunkRadius()));
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
		V1_19Client.listener(listener, layers, registries);
		connection.packetListener = listener;
		return listener;
	}

	@Override
	public BlockProbe<?, ?> blocks() {
		ClientPacketListener listener = listener(new RecordingConnection());
		ClientLevel level = V1_19Levels.loaded(listener, registries, minecraft.renderer(), 1);
		return new V1_19Blocks(level, new V1_19Release.ProbePlayer(minecraft, level, listener));
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
