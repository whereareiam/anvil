package me.whereareiam.anvil.testkit.tests.server.physics.sonar;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsCourse;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsEnvironment;
import me.whereareiam.anvil.testkit.tests.server.scenario.PhysicsScenarioFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shows that Sonar verifies players on the release: a player without client physics, which never falls, fails its
 * verification and never reaches the backend.
 */
@Tag("sonar")
@PhysicsEnvironment(PhysicsEnvironment.Judge.SONAR)
class SonarControlTest {
	private static final Duration VERIFICATION = Duration.ofSeconds(30);
	private static final List<String> MOVEMENT_FAILURES = List.of("position", "gravity", "motion", "collision",
			"fell through blocks", "offset");

	@Test
	void failsAPlayerWithoutPhysics(ScenarioContext anvil) {
		ConsoleWatch proxy = anvil.processes().proxy(PhysicsScenarioFactory.PROXY).console().watch();
		PlayerWatch backend = anvil.processes().server(PhysicsScenarioFactory.BACKEND).capability(Players.class).watch();
		SimulatedPlayer player = anvil.players().create("SonarControl");
		Session session = player.capability(Session.class);

		session.connect();

		String reason = session.kicked(VERIFICATION);
		assertTrue(reason.contains("You have failed the bot verification"), "Sonar disconnected the player with: " + reason);
		String failure = proxy.printed(line -> line.contains(player.name() + "/")
				&& line.contains("has failed the bot check for"), PhysicsCourse.TIMEOUT);
		// A vanilla client answers Sonar's teleport with its position and then falls; Sonar must fail this player at
		// one of those steps, not for anything else a client sends.
		assertTrue(MOVEMENT_FAILURES.stream().anyMatch(failure::contains),
				"Sonar must fail the player for its movement, but logged: " + failure);
		backend.neverJoined(player, Duration.ofSeconds(2));
	}
}
