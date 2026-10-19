package me.whereareiam.anvil.testkit.tests.server.physics.sonar;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsCourse;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsEnvironment;
import me.whereareiam.anvil.testkit.tests.server.scenario.PhysicsScenarioFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player with client physics falls and lands in Sonar's verification world as a client does, passes the
 * verification, and reaches the backend when it connects again once the proxy's login rate limit lets the address log
 * in again, as a player that reconnects by hand does.
 */
@Tag("sonar")
@PhysicsEnvironment(PhysicsEnvironment.Judge.SONAR)
class SonarVerificationTest {
	private static final Duration VERIFICATION = Duration.ofSeconds(30);
	/**
	 * Velocity refuses a login from an address that logged in less than this ago, its default {@code login-ratelimit}.
	 */
	private static final Duration LOGIN_RATE_LIMIT = Duration.ofSeconds(3);

	@Test
	void passesAPlayerWithPhysicsAndLetsItThrough(ScenarioContext anvil) {
		ConsoleWatch proxy = anvil.processes().proxy(PhysicsScenarioFactory.PROXY).console().watch();
		PlayerWatch backend = anvil.processes().server(PhysicsScenarioFactory.BACKEND).capability(Players.class).watch();
		SimulatedPlayer player = anvil.players().create("SonarPhysics");
		player.capability(Movement.class).physics(true);
		Session session = player.capability(Session.class);

		session.connect();

		String reason = session.kicked(VERIFICATION);
		assertTrue(reason.contains("You have successfully passed the verification"),
				"Sonar disconnected the player with: " + reason);
		proxy.printed(player.name() + " has been verified successfully", PhysicsCourse.TIMEOUT);
		// The player logged in before Sonar disconnected it, so the limit has passed once as long again passed since.
		pause(LOGIN_RATE_LIMIT);
		session.rejoin();
		session.connected(PhysicsCourse.TIMEOUT);
		backend.joined(player, PhysicsCourse.TIMEOUT);
	}

	private static void pause(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while the proxy's login rate limit passes", interrupted);
		}
	}
}
