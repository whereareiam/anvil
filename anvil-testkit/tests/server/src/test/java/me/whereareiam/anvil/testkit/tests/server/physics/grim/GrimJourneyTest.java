package me.whereareiam.anvil.testkit.tests.server.physics.grim;

import me.whereareiam.anvil.testkit.tests.server.physics.CourseJourneyTest;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsEnvironment;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;

/**
 * Walks the course's journeys with client physics on a Paper server with Grim, which predicts every movement exactly
 * and must flag none of them, besides everything the vanilla server checks.
 */
@Tag("grim")
@PhysicsEnvironment(PhysicsEnvironment.Judge.GRIM)
class GrimJourneyTest extends CourseJourneyTest {
	@Override
	protected @NotNull PhysicsEnvironment.Judge judge() {
		return PhysicsEnvironment.Judge.GRIM;
	}
}
