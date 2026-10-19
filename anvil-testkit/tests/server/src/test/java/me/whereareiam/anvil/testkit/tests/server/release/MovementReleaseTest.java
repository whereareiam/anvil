package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Moves the player, which the server sees, and lets the vanilla client's physics walk it without a correction.
 */
@Release
class MovementReleaseTest {
	/**
	 * The first release Anvil ships no block data for, which client physics needs.
	 */
	private static final String WITHOUT_BLOCK_DATA = "26.2";
	private static final MovementInput WALK = MovementInput.builder().forward(true).jump(true).build();

	@Test
	void movesThePlayerWhereTheServerSeesIt(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Messages messages = player.capability(Messages.class);

		Position position = serverPosition(server, player);
		player.capability(Movement.class).move(position.toBuilder().x(position.getX() + 0.15D).onGround(true).build());

		messages.received("anvil:move", Duration.ofSeconds(10));
	}

	@Test
	void fallsAndWalksWithClientPhysicsWithoutACorrection(ScenarioContext anvil) {
		assumeFalse(TestedRelease.isAtLeast(WITHOUT_BLOCK_DATA), "Anvil ships no block data from " + WITHOUT_BLOCK_DATA);
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Movement movement = player.capability(Movement.class);

		movement.physics(true);
		awaitGround(movement);
		Position landed = movement.position();
		movement.input(WALK);
		movement.awaitTicks(60);
		movement.input(MovementInput.NONE);
		awaitGround(movement);
		movement.awaitTicks(30);

		Position walked = movement.position();
		double distance = Math.hypot(walked.getX() - landed.getX(), walked.getZ() - landed.getZ());
		assertTrue(distance > 1.0, "the player walked " + distance + " blocks from " + landed + " to " + walked);
		Position seen = serverPosition(server, player);
		assertEquals(walked.getX(), seen.getX(), 1.0E-3);
		assertEquals(walked.getY(), seen.getY(), 1.0E-3);
		assertEquals(walked.getZ(), seen.getZ(), 1.0E-3);
		List<String> corrections = anvil.processes().server("server").console().tail(1000).stream()
				.filter(line -> line.contains(player.name()) && (line.contains("moved wrongly") || line.contains("moved too quickly")))
				.toList();
		assertEquals(List.of(), corrections);
	}

	@Test
	void refusesClientPhysicsWithoutBlockData(ScenarioContext anvil) {
		assumeTrue(TestedRelease.isAtLeast(WITHOUT_BLOCK_DATA), "Anvil ships block data before " + WITHOUT_BLOCK_DATA);
		FixtureServer server = new FixtureServer(anvil);
		Movement movement = server.join().capability(Movement.class);

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> movement.physics(true));

		assertTrue(failure.getMessage().contains("No block data for Minecraft"), failure.getMessage());
	}

	private static void awaitGround(@NotNull Movement movement) {
		for (int tick = 0; tick < 200 && !movement.position().isOnGround(); tick++) movement.awaitTicks(1);

		assertTrue(movement.position().isOnGround(), "the player reached the ground at " + movement.position());
	}

	private static @NotNull Position serverPosition(@NotNull FixtureServer server, @NotNull SimulatedPlayer player) {
		player.capability(Messages.class).command("anvil-fixture position");
		String[] position = server.reply(player, "anvil:position:").split(":");

		return Position.builder()
				.x(Double.parseDouble(position[0]))
				.y(Double.parseDouble(position[1]))
				.z(Double.parseDouble(position[2]))
				.yaw(Float.parseFloat(position[3]))
				.pitch(Float.parseFloat(position[4]))
				.build();
	}
}
