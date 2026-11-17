package me.whereareiam.anvil.testkit.tests.server.physics;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.movement.ClientPhysics;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.packets.PacketTap;
import me.whereareiam.anvil.capability.packets.Packets;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.capability.packets.model.TappedPacket;
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.capability.world.block.BlockQuery;
import me.whereareiam.anvil.capability.world.block.BlockView;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import me.whereareiam.anvil.testkit.tests.server.release.Release;
import me.whereareiam.anvil.testkit.tests.server.release.TestedRelease;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs each physics course with client physics on the Paper server of the release under test, which must take the
 * player's movement as a vanilla client's: it may never correct the player's position nor warn that the player moved
 * too quickly or wrongly. The course is built on a cleared site, never on the terrain of the world's random seed, and
 * the player is placed at its start, or where its join puts it, at rest before the watches open and physics turns on.
 */
@Release
class CourseTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	/**
	 * How often the test looks at where the client is while it waits for the teleport to the start.
	 */
	private static final Duration POLL = Duration.ofMillis(10);
	/**
	 * How long the console may still report the course's last movements after physics turned off.
	 */
	private static final Duration SETTLE = Duration.ofSeconds(2);
	/**
	 * The air cleared around a course's bounds: sideways and below, and above, where the player's head and jumps
	 * reach.
	 */
	private static final int MARGIN = 4;
	private static final int HEADROOM = 5;
	/**
	 * What a server logs when it refuses a movement: Paper and vanilla warn that the player moved too quickly or
	 * wrongly, and kick a player whose movement looks like flight.
	 */
	private static final List<String> WARNINGS = List.of("moved too quickly", "moved wrongly", "Flying is not enabled");

	/**
	 * The courses the release runs whose server this suite's Paper server can be: those that script no packet of the
	 * server's own, which only the reference harness sends.
	 */
	static List<Course> courses() {
		return Course.all().stream().filter(course -> course.runsOn(TestedRelease.version()) && !course.isScriptsServer())
				.toList();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("courses")
	void runsTheCourseWithoutAPositionCorrection(Course course, ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		RunningProcess process = anvil.processes().server("server");
		SimulatedPlayer player = server.join();
		Movement movement = player.capability(Movement.class);
		Packets packets = player.capability(Packets.class);

		build(server, process, course);
		place(server, player, course);

		ClientPhysics physics = movement.physics();
		ConsoleWatch console = process.console().watch();
		try (PacketTap corrections = packets.tap(PacketSelection.of(ClientboundPlayerPosition.class))) {
			walk(physics, course);
			assertEquals(Optional.empty(), physics.failure(), "Client physics failed on course '" + course.getName()
					+ "'");
			physics.disable();

			console.neverPrinted(line -> WARNINGS.stream().anyMatch(line::contains), SETTLE);
			assertEquals(List.of(), corrections.history().stream().map(CourseTest::describe).toList(),
					"The server corrected the position of " + player.name() + " on course '" + course.getName()
							+ "' on Minecraft " + TestedRelease.version());
		}
	}

	/**
	 * Clears the course's site to air, lays its boxes in order and keeps every mob off it, each block set as the
	 * course gives it without the updates that would reshape it.
	 */
	private static void build(FixtureServer server, RunningProcess process, Course course) {
		Course.Box site = course.bounds().grown(MARGIN, MARGIN, HEADROOM);
		server.console("difficulty peaceful");
		fill(server, process, site, "minecraft:air");
		for (Course.Box box : course.getBlocks())
			fill(server, process, box, box.state());
		server.console("kill @e[type=!minecraft:player,x=" + site.min(0) + ",y=" + site.min(1) + ",z=" + site.min(2)
				+ ",dx=" + (site.max(0) - site.min(0)) + ",dy=" + (site.max(1) - site.min(1)) + ",dz="
				+ (site.max(2) - site.min(2)) + "]");
	}

	private static void fill(FixtureServer server, RunningProcess process, Course.Box box, String state) {
		ConsoleWatch console = process.console().watch();
		server.console("anvil-blocks " + box.from()[0] + " " + box.from()[1] + " " + box.from()[2] + " "
				+ box.to()[0] + " " + box.to()[1] + " " + box.to()[2] + " " + state);
		String result = console.printed("anvil:blocks:", TIMEOUT);
		if (!result.contains("anvil:blocks:filled")) fail("The server did not lay " + state + ": " + result);
	}

	/**
	 * Teleports the player to the course's start in survival and waits until its client knows the whole site, stands
	 * at the start and the server sees it there.
	 */
	private static void place(FixtureServer server, SimulatedPlayer player, Course course) {
		Course.Start start = course.getStart();
		server.console("gamemode survival " + player.name());
		try (PacketTap teleports = player.capability(Packets.class)
				.tap(PacketSelection.of(ClientboundPlayerPosition.class))) {
			server.console("tp " + player.name() + " " + start.x() + " " + start.y() + " " + start.z() + " "
					+ start.yaw() + " " + start.pitch());
			teleports.received(ClientboundPlayerPosition.class, teleport -> teleport.getPosition().getX() == start.x()
					&& teleport.getPosition().getY() == start.y() && teleport.getPosition().getZ() == start.z(), TIMEOUT);
		}

		awaitSite(player.capability(World.class).blocks(), course);
		player.capability(Messages.class).command("anvil-fixture position");
		String[] seen = server.reply(player, "anvil:position:").split(":");
		assertEquals(List.of(start.x(), start.y(), start.z()), List.of(Double.parseDouble(seen[0]),
				Double.parseDouble(seen[1]), Double.parseDouble(seen[2])), "Where the server placed the player");
		awaitClientAt(player.capability(Movement.class), start);
	}

	/**
	 * Waits until the client knows the cleared site's top in every chunk it spans, and every box's corners as the
	 * course leaves them.
	 */
	private static void awaitSite(BlockView blocks, Course course) {
		Course.Box site = course.bounds().grown(MARGIN, MARGIN, HEADROOM);
		int top = site.max(1);
		for (int chunkX = site.min(0) >> 4; chunkX <= site.max(0) >> 4; chunkX++)
			for (int chunkZ = site.min(2) >> 4; chunkZ <= site.max(2) >> 4; chunkZ++) {
				int x = Math.clamp(chunkX * 16L, site.min(0), site.max(0));
				int z = Math.clamp(chunkZ * 16L, site.min(2), site.max(2));
				blocks.until(new BlockPosition(x, top, z), BlockQuery.air(), TIMEOUT);
			}

		for (Course.Box box : course.getBlocks())
			for (int[] corner : List.of(box.from(), box.to())) {
				String state = course.stateAt(corner[0], corner[1], corner[2]);
				int properties = state.indexOf('[');
				blocks.until(new BlockPosition(corner[0], corner[1], corner[2]),
						BlockQuery.named(properties < 0 ? state : state.substring(0, properties)), TIMEOUT);
			}
	}

	/**
	 * Waits until the client stands where the server placed it, which it applies after the teleport arrives.
	 */
	private static void awaitClientAt(Movement movement, Course.Start start) {
		Instant deadline = Instant.now().plus(TIMEOUT);
		Predicate<Course.Start> placed = expected -> movement.position()
				.filter(position -> position.getX() == expected.x() && position.getY() == expected.y()
						&& position.getZ() == expected.z())
				.isPresent();
		while (!placed.test(start)) {
			if (Instant.now().isAfter(deadline))
				fail("The client did not stand at the course's start; it is at " + movement.position());
			LockSupport.parkNanos(POLL.toNanos());
		}
	}

	/**
	 * Holds the course's keys tick by tick: none before the first entry, then each entry's from its tick on.
	 */
	private static void walk(ClientPhysics physics, Course course) {
		List<Course.Keys> keys = course.getKeys();
		boolean fromFirstTick = !keys.isEmpty() && keys.getFirst().tick() == 0;
		physics.input(fromFirstTick ? keys.getFirst().input() : MovementInput.NONE);
		physics.enable();

		int tick = 0;
		for (Course.Keys entry : keys) {
			if (entry.tick() == 0) continue;

			physics.awaitTicks(entry.tick() - tick, TIMEOUT);
			physics.input(entry.input());
			tick = entry.tick();
		}
		physics.awaitTicks(course.getTicks() - tick, TIMEOUT);
	}

	private static String describe(TappedPacket packet) {
		return packet.packet(ClientboundPlayerPosition.class)
				.map(teleport -> "to " + teleport.getPosition() + " relative " + teleport.getRelatives())
				.orElseGet(packet::toString);
	}
}
