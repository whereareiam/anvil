package me.whereareiam.anvil.testkit.tests.server.physics.sonar;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.testkit.tests.server.physics.Hover;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsCourse;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsEnvironment;
import me.whereareiam.anvil.testkit.tests.server.scenario.PhysicsScenarioFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shows that Sonar verifies players on the release: a player without client physics that moves through the air where
 * Sonar placed it fails the verification for its movement and never reaches the backend. From Minecraft 1.21.2 Sonar
 * fails it for a position without the end of a client tick; before, for moving away from the platform without falling.
 */
@Tag("sonar")
@PhysicsEnvironment(PhysicsEnvironment.Judge.SONAR)
class SonarControlTest {
	private static final Duration VERIFICATION = Duration.ofSeconds(30);
	private static final String VERIFYING = "You are being verified";
	private static final List<String> MOVEMENT_FAILURES = List.of("position", "gravity", "motion", "collision",
			"fell through blocks", "offset");

	@Test
	void failsAPlayerWithoutPhysics(ScenarioContext anvil) {
		ConsoleWatch proxy = anvil.processes().proxy(PhysicsScenarioFactory.PROXY).console().watch();
		PlayerWatch backend = anvil.processes().server(PhysicsScenarioFactory.BACKEND).capability(Players.class).watch();
		SimulatedPlayer player = anvil.players().create("SonarControl");
		Session session = player.capability(Session.class);

		session.connect();
		player.capability(Messages.class).received(VERIFYING, PhysicsCourse.TIMEOUT);
		Position placed = player.capability(Movement.class).position();
		Hover.hold(player, placed, () -> false, VERIFICATION);

		String reason = session.kicked(PhysicsCourse.TIMEOUT);
		assertTrue(reason.contains("You have failed the bot verification"), "Sonar disconnected the player with: " + reason);
		String failure = proxy.printed(line -> line.contains(player.name() + "/")
				&& line.contains("has failed the bot check for"), PhysicsCourse.TIMEOUT);
		// Sonar must fail the player for its movement, not for anything else a client sends.
		assertTrue(MOVEMENT_FAILURES.stream().anyMatch(failure::contains),
				"Sonar must fail the player for its movement, but logged: " + failure);
		backend.neverJoined(player, Duration.ofSeconds(2));
	}
}
