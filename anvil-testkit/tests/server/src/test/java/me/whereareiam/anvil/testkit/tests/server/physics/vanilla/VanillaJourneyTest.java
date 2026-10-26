package me.whereareiam.anvil.testkit.tests.server.physics.vanilla;

import me.whereareiam.anvil.testkit.tests.server.physics.CourseJourneyTest;
import me.whereareiam.anvil.testkit.tests.server.physics.PhysicsEnvironment;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;

/**
 * Walks the course's journeys with client physics on the release's own Paper server, which must force no position on
 * the player, log no complaint about its moves, deal vanilla's fall damage, and see the player where its client is.
 */
@Tag("vanilla")
@PhysicsEnvironment(PhysicsEnvironment.Judge.VANILLA)
class VanillaJourneyTest extends CourseJourneyTest {
	@Override
	protected @NotNull PhysicsEnvironment.Judge judge() {
		return PhysicsEnvironment.Judge.VANILLA;
	}
}
