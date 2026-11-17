package me.whereareiam.anvil.testkit.physics.harness.v1_16;

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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;

import java.io.File;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The vanilla client of Minecraft 1.16.5 to 1.18.2, whose registries are the game's built-in registry access, whose
 * tags bind through the tag manager of the data pack, and whose input is the boolean {@code Input}.
 *
 * <p>The three releases name the same members, except where noted: the client level takes a dimension type until
 * 1.18.2, which takes its holder and a simulation distance; a level chunk takes a biome container until 1.18.2; the
 * tag manager takes the registry access from 1.17.1; and tags bind to static wrappers until 1.18.2, which binds them
 * to the registries' holders. Each release's {@link V1_16Release}, in its own {@code release-<minecraft>} sources,
 * reaches those members.
 */
public final class V1_16Harness implements VanillaClient {
	private static final GameProfile PROFILE = new GameProfile(new UUID(0L, 1L), VanillaHarness.PROFILE_NAME);

	private HeadlessMinecraft minecraft;
	private Options options;
	private RegistryAccess registries;
	private ClientboundUpdateTagsPacket tags;

	/**
	 * Runs the outputs on the client this JVM's class path holds.
	 *
	 * @param arguments see {@link VanillaHarness#run(String[], VanillaClient)}
	 */
	public static void main(String[] arguments) throws Exception {
		VanillaHarness.run(arguments, new V1_16Harness());
	}

	@Override
	public String bootstrap() throws Exception {
		V1_16Release.detectVersion();
		Bootstrap.bootStrap();
		registries = V1_16Release.builtinRegistries();
		FallbackResourceManager resources = new FallbackResourceManager(PackType.SERVER_DATA, "minecraft");
		resources.add(new ServerPacksSource().vanillaPack);
		tags = V1_16Release.bindTags(resources, registries);

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
		V1_16Client.setup(minecraft);

		// Sounds reach a sound engine that never loaded, as on a machine without audio: nothing plays.
		SoundManager sounds = Unconstructed.allocate(SoundManager.class);
		sounds.soundEngine = Unconstructed.allocate(SoundEngine.class);
		minecraft.soundManager = sounds;

		// Particles reach a renderer whose camera was never set up, which drops them; no screen is drawn.
		GameRenderer renderer = Unconstructed.allocate(GameRenderer.class);
		renderer.mainCamera = new Camera();
		minecraft.gameRenderer = renderer;
		HeadlessLevelRenderer levelRenderer = Unconstructed.allocate(HeadlessLevelRenderer.class);
		levelRenderer.sections = new CompiledSections();
		levelRenderer.minecraft = minecraft;
		minecraft.levelRenderer = levelRenderer;
		minecraft.debugRenderer = new DebugRenderer(minecraft);

		return SharedConstants.getCurrentVersion().getName();
	}

	@Override
	public CourseRun start(Course course) {
		minecraft.player = null;
		minecraft.level = null;
		minecraft.screen = null;
		RecordingConnection connection = new RecordingConnection();
		ClientPacketListener listener = listener(connection);
		if (course.getJoin() == null) {
			V1_16Placement.place(minecraft, listener, course, registries);
			return new V1_16Run(minecraft, connection, keys(), List.of());
		}

		// The server's own level, which holds the course's blocks it sends as chunks.
		ClientLevel server = V1_16Levels.loaded(listener(new RecordingConnection()), registries, minecraft.renderer(), course.chunkRadius());
		V1_16Levels.lay(server, course);
		V1_16Placement.join(minecraft, listener, course, registries, tags);
		return new V1_16Run(minecraft, connection, keys(), V1_16Client.chunks(listener, server, course.chunkRadius()));
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
		V1_16Client.listener(listener, registries);
		connection.packetListener = listener;
		return listener;
	}

	@Override
	public BlockProbe<?, ?> blocks() {
		ClientPacketListener listener = listener(new RecordingConnection());
		ClientLevel level = V1_16Levels.loaded(listener, registries, minecraft.renderer(), 1);
		return new V1_16Blocks(level, new V1_16Release.ProbePlayer(minecraft, level, listener));
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
