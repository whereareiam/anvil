package me.whereareiam.anvil.testkit.tests.server.physics.grim;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.testkit.tests.server.physics.Hover;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsCourse;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shows that Grim is active on the release: it flags a player without client physics that hovers in the air instead
 * of falling, as it flags an idle bot.
 */
@Tag("grim")
@PhysicsEnvironment(PhysicsEnvironment.Judge.GRIM)
class GrimControlTest {
	@Test
	void flagsAPlayerThatHoversWithoutPhysics(ScenarioContext anvil) {
		PhysicsCourse course = new PhysicsCourse(anvil, PhysicsEnvironment.Judge.GRIM);
		course.build();
		SimulatedPlayer player = course.join(ignored -> { });
		course.place(player, PhysicsCourse.Lane.HOVER);
		PhysicsCourse.Verdict verdict = course.verdict(player);

		Hover.hold(player, PhysicsCourse.Lane.HOVER, () -> false, Duration.ofSeconds(3));

		String flag = verdict.awaitFlagged();
		assertTrue(flag.contains(player.name()), flag);
	}
}
