package me.whereareiam.anvil.testkit.tests.server.physics;

import me.whereareiam.anvil.integration.junit.AnvilEnvironment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs a test of the physics suite against one judge of the release under test, which {@link PhysicsRelease} names.
 * The judge's Paper server is kept for the test JVM and builds its course once per test; each test gets a scenario,
 * and so players, of its own, and each Sonar proxy starts fresh.
 *
 * <pre>{@code
 * @Tag("grim")
 * @PhysicsEnvironment(PhysicsEnvironment.Judge.GRIM)
 * class GrimControlTest {
 *     @Test
 *     void flagsAHoveringPlayer(ScenarioContext anvil) {
 *         PhysicsCourse course = new PhysicsCourse(anvil, PhysicsEnvironment.Judge.GRIM);
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@AnvilEnvironment(PhysicsScenarios.class)
public @interface PhysicsEnvironment {
	/**
	 * Returns who judges the player's movement.
	 *
	 * @return the judge
	 */
	Judge value();

	/**
	 * Who judges a player's movement. A test class carries the judge's name as its JUnit tag, which
	 * {@code -PanvilPhysicsJudges} selects.
	 */
	enum Judge {
		/**
		 * The release's own Paper server, which forces a position on a player it does not believe.
		 */
		VANILLA,
		/**
		 * The Grim anti-cheat on a Paper server, which predicts every movement exactly and flags each one it cannot.
		 */
		GRIM,
		/**
		 * The Sonar anti-bot on a Velocity proxy, which lets a player through only once it fell and landed as a client
		 * does.
		 */
		SONAR
	}
}
