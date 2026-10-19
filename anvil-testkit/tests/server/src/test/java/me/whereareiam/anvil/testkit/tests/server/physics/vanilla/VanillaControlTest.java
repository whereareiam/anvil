package me.whereareiam.anvil.testkit.tests.server.physics.vanilla;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.testkit.tests.server.physics.Hover;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsCourse;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shows that the release's own server judges movement: a player without client physics that hovers in the air is
 * kicked for flying.
 */
@Tag("vanilla")
@PhysicsEnvironment(PhysicsEnvironment.Judge.VANILLA)
class VanillaControlTest {
	@Test
	void kicksAPlayerThatHoversWithoutPhysics(ScenarioContext anvil) {
		PhysicsCourse course = new PhysicsCourse(anvil, PhysicsEnvironment.Judge.VANILLA);
		course.build();
		SimulatedPlayer player = course.join(ignored -> { });
		Session session = player.capability(Session.class);
		course.place(player, PhysicsCourse.Lane.HOVER);

		boolean kicked = Hover.hold(player, PhysicsCourse.Lane.HOVER, () -> session.state().kickReason() != null,
				PhysicsCourse.TIMEOUT);

		assertTrue(kicked, "The server did not kick the hovering player");
		String reason = session.state().kickReason();
		assertTrue(reason.toLowerCase(Locale.ROOT).contains("flying"), "The server kicked the player for: " + reason);
	}
}
