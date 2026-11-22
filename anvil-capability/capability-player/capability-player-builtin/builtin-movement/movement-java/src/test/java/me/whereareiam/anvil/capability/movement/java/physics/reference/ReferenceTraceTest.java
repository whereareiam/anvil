package me.whereareiam.anvil.capability.movement.java.physics.reference;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.java.physics.ReferenceClient;
import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.java.physics.reference.ReferenceTrace.Sample;
import me.whereareiam.anvil.edition.java.physics.living.LivingBody;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs every physics course of {@code anvil-testkit/physics/courses} on Anvil's client in every release whose vanilla
 * client left reference traces in {@code anvil-testkit/physics/traces}, started as the harness starts the vanilla
 * client: a survival login of the release's client version and protocol, then either the player placed at rest in a
 * level whose chunks around it are loaded with the course's blocks, or the join's first position packet, the event that
 * starts the level's loading where the server sends it, and the course's chunks after the join's ticks; the pings of
 * the join's handshake arrive between the login and the first position packet. The keys and the server's teleports,
 * pings and animations of the player follow the course. Every tick's position, delta movement, ground and horizontal
 * collision, and the packets the client sent during it, in order, must match the vanilla client's bit for bit; a join
 * compares its handshake as tick {@code -1}. A course whose {@code since} a release predates has no trace there.
 */
class ReferenceTraceTest {
	private static final Path COURSES = directory("anvil.physics.courses");
	private static final Path TRACES = directory("anvil.physics.traces");
	private static final MinecraftVersion LEVEL_LOADING_EVENT = MinecraftVersion.parse("1.20.3");

	@TestFactory
	@NotNull Stream<DynamicContainer> matchesTheVanillaClientTickForTick() {
		List<ReferenceCourse> courses = list(COURSES).filter(file -> file.toString().endsWith(".toml"))
				.map(ReferenceCourse::read).toList();
		assertFalse(courses.isEmpty(), "no course in " + COURSES);

		return list(TRACES).filter(Files::isDirectory).map(release -> DynamicContainer.dynamicContainer(
				release.getFileName().toString(), courses.stream().map(course -> DynamicTest.dynamicTest(course.name(),
						() -> check(release, course)))));
	}

	private static void check(@NotNull Path directory, @NotNull ReferenceCourse course) {
		String release = directory.getFileName().toString();
		Path trace = directory.resolve(course.name() + ".trace");
		if (!course.runsOn(MinecraftVersion.parse(release))) {
			assertFalse(Files.exists(trace), release + " predates " + course.name() + ", yet has a trace of it");
			return;
		}
		run(release, course, ReferenceTrace.read(trace));
	}

	private static void run(@NotNull String release, @NotNull ReferenceCourse course, @NotNull ReferenceTrace.Trace trace) {
		List<ReferenceTrace.Tick> expected = trace.ticks();
		assertEquals(course.ticks() + (course.join() == null ? 0 : 1), expected.size(), release + " " + course.name() + ": the trace's ticks");
		MinecraftVersion version = MinecraftVersion.parse(release);
		ReferencePackets lines = new ReferencePackets(version);
		try (ReferenceClient client = ReferenceClient.join(release)) {
			TestClient world = client.client();
			int protocol = world.player.protocol(JavaProtocol.class).protocolNumber();
			assertEquals(protocol, world.registry.protocol(), release + ": Anvil ships no block data of protocol " + protocol);
			for (ReferenceCourse.Blocks box : course.blocks()) lay(world, box);

			int index = 0;
			if (course.join() == null) {
				loadChunks(world, course);
				client.place(course.start());
			} else {
				for (ServerPacket packet : course.handshake()) world.player.deliver(packet.packet(version));
				world.player.deliver(Teleport.first(course.join().at()).packet(version));
				if (version.isAtLeast(LEVEL_LOADING_EVENT))
					world.player.deliver(new ClientboundGameEvent(GameEventType.LEVEL_CHUNKS_LOAD_START, 0.0F));
				compare(release, course, client, lines, expected.get(index++));
			}
			for (int tick = 0; tick < course.ticks(); tick++) {
				if (course.join() != null && tick == course.join().chunksAfterTicks()) loadChunks(world, course);
				for (ServerPacket packet : course.serverBefore(tick)) world.player.deliver(packet.packet(version));
				client.tick(course.heldOn(tick));
				compare(release, course, client, lines, expected.get(index++));
			}
		}
	}

	private static void compare(
			@NotNull String release,
			@NotNull ReferenceCourse course,
			@NotNull ReferenceClient client,
			@NotNull ReferencePackets lines,
			@NotNull ReferenceTrace.Tick vanilla
	) {
		int tick = vanilla.state().tick();
		LivingBody body = client.body();
		assertNotNull(body, release + " " + course.name() + ": no simulated player on tick " + tick);
		Sample anvil = Sample.of(tick, body);
		List<String> sent = client.sent().stream().map(lines::line).toList();
		if (anvil.same(vanilla.state()) && sent.equals(vanilla.packets())) return;

		fail(release + " " + course.name() + " differs on tick " + tick + "\n  vanilla: " + vanilla.state() + "\n  anvil:   " + anvil
				+ "\n  vanilla sent: " + vanilla.packets() + "\n  anvil sent:   " + sent);
	}

	/**
	 * Sends the columns within the course's radius around the origin, with the blocks laid so far, as chunk packets.
	 */
	private static void loadChunks(@NotNull TestClient client, @NotNull ReferenceCourse course) {
		int radius = course.chunkRadius();
		for (int x = -radius; x <= radius; x++)
			for (int z = -radius; z <= radius; z++) client.load(x, z);
		client.send();
	}

	private static void lay(@NotNull TestClient client, @NotNull ReferenceCourse.Blocks box) {
		for (int x = Math.min(box.from()[0], box.to()[0]); x <= Math.max(box.from()[0], box.to()[0]); x++)
			for (int y = Math.min(box.from()[1], box.to()[1]); y <= Math.max(box.from()[1], box.to()[1]); y++)
				for (int z = Math.min(box.from()[2], box.to()[2]); z <= Math.max(box.from()[2], box.to()[2]); z++)
					client.set(x, y, z, box.block(), box.properties());
	}

	private static @NotNull Path directory(@NotNull String property) {
		String directory = System.getProperty(property);
		assertTrue(directory != null && !directory.isBlank(), "The test needs the directory in the system property " + property);
		return Path.of(directory);
	}

	private static @NotNull Stream<Path> list(@NotNull Path directory) {
		try (Stream<Path> files = Files.list(directory)) {
			return files.sorted().toList().stream();
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not list " + directory, failure);
		}
	}
}
