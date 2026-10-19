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

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Shows that Sonar verifies players on the release: a player without client physics that hovers where Sonar placed it
 * is never verified and never reaches the backend. From Minecraft 1.21.2 Sonar fails such a player for a position
 * without the end of a client tick; before, it lets the player hover until it closes the connection, at the latest
 * after the most packets a verification may take, without a verdict.
 */
@Tag("sonar")
@PhysicsEnvironment(PhysicsEnvironment.Judge.SONAR)
class SonarControlTest {
	private static final Duration VERIFICATION = Duration.ofSeconds(30);
	private static final String VERIFYING = "You are being verified";
	private static final String PASSED = "You have successfully passed the verification";
	private static final String FAILED_CHECK = "has failed the bot check for";
	private static final List<String> MOVEMENT_FAILURES = List.of("position", "gravity", "motion", "collision",
			"fell through blocks", "offset");

	@Test
	void neverLetsThroughAPlayerWithoutPhysics(ScenarioContext anvil) {
		ConsoleWatch proxy = anvil.processes().proxy(PhysicsScenarioFactory.PROXY).console().watch();
		PlayerWatch backend = anvil.processes().server(PhysicsScenarioFactory.BACKEND).capability(Players.class).watch();
		SimulatedPlayer player = anvil.players().create("SonarControl");
		Session session = player.capability(Session.class);

		session.connect();
		player.capability(Messages.class).received(VERIFYING, PhysicsCourse.TIMEOUT);
		Position placed = player.capability(Movement.class).position();
		Hover.hold(player, placed, () -> false, VERIFICATION);

		assertFalse(session.state().connected(), "Sonar kept the hovering player for " + VERIFICATION);
		String reason = session.state().kickReason();
		assertFalse(reason != null && reason.contains(PASSED), "Sonar verified the hovering player");
		// Sonar must refuse the player for its movement, if it names a reason, not for anything else a client sends.
		proxy.neverPrinted(line -> line.contains(player.name() + "/") && line.contains(FAILED_CHECK)
				&& MOVEMENT_FAILURES.stream().noneMatch(line::contains), Duration.ofSeconds(1));
		backend.neverJoined(player, Duration.ofSeconds(2));
	}
}
