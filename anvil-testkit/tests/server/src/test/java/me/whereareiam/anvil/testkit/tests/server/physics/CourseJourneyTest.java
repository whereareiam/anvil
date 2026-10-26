package me.whereareiam.anvil.testkit.tests.server.physics;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The journeys a player with client physics walks across the physics course, each judged by the judge of the subclass:
 * falls from three heights, a walk across stone, ice, slime and honey, steps onto a slab and stairs, walks into a
 * fence and a wall, a jump onto a block, a sprint-jump, a sneak to an edge, a swim up through water and a climb up a
 * ladder. After each journey the judge must have refused none of the player's movement, and the server must see the
 * player where its client is, within the distance a vanilla client moves before it reports a new position.
 */
public abstract class CourseJourneyTest {
	/**
	 * The distance a vanilla client moves before it sends a new position, and so how far the server's view of a
	 * player may lag behind its client.
	 */
	private static final double REPORTED = 2.0E-4D;
	/**
	 * How close a collision leaves a player to where vanilla's collision does.
	 */
	private static final double EXACT = 1.0E-6D;
	/**
	 * A vanilla client reports its position at least once every 20 ticks, even when it stands still.
	 */
	private static final int REMINDER_TICKS = 20;
	private static final int SETTLE_TICKS = 200;
	private static final MovementInput FORWARD = MovementInput.builder().forward(true).build();

	/**
	 * Returns the judge the subclass's environment declares.
	 *
	 * @return the judge
	 */
	protected abstract @NotNull PhysicsEnvironment.Judge judge();

	@Test
	void fallsThreeBlocksWithoutDamage(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.FALL_3);

		journey.walkOff();

		assertEquals(PhysicsCourse.FLOOR, journey.movement().position().getY(), EXACT);
		assertEquals(20.0F, journey.course().health(journey.player()));
		journey.assertAccepted();
	}

	@Test
	void fallsTenBlocksAndTakesSevenDamage(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.FALL_10);

		journey.walkOff();

		assertEquals(PhysicsCourse.FLOOR, journey.movement().position().getY(), EXACT);
		assertEquals(13.0F, journey.course().health(journey.player()));
		journey.assertAccepted();
	}

	@Test
	void diesFallingTwentyFourBlocks(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.FALL_24);

		journey.movement().input(FORWARD);
		journey.verdict().awaitFatalFall();

		journey.verdict().assertAccepted();
	}

	@Test
	void crossesIceSlimeAndHoney(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.SURFACES);
		double[] fastest = new double[2];

		journey.walk(FORWARD, position -> {
			double speed = Math.abs(journey.movement().motion().getX());
			if (position.getX() > 10 && position.getX() < 15) fastest[0] = Math.max(fastest[0], speed);
			if (position.getX() > 26 && position.getX() < 31) fastest[1] = Math.max(fastest[1], speed);
			return position.getX() > 34;
		}, 900);
		journey.settle(MovementInput.NONE);

		assertTrue(fastest[1] < fastest[0], "Honey must slow the player below its speed on ice: " + fastest[1] + " and "
				+ fastest[0]);
		assertEquals(PhysicsCourse.FLOOR, journey.movement().position().getY(), EXACT);
		journey.assertAccepted();
	}

	@Test
	void stepsOntoASlabAndUpStairs(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.STEPS);
		double[] onSlab = new double[1];

		journey.walk(FORWARD, position -> {
			if (position.getX() > 8.3 && position.getX() < 8.7) onSlab[0] = Math.max(onSlab[0], position.getY());
			return position.getX() > 14.5;
		}, 300);
		journey.settle(MovementInput.NONE);

		assertEquals(PhysicsCourse.FLOOR + 0.5, onSlab[0], EXACT);
		assertEquals(PhysicsCourse.FLOOR + 1, journey.movement().position().getY(), EXACT);
		journey.assertAccepted();
	}

	@Test
	void stopsAgainstAFence(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.FENCE);

		journey.walkFor(FORWARD, 60);
		journey.settle(MovementInput.NONE);

		assertEquals(8.375 - 0.3, journey.movement().position().getX(), EXACT);
		journey.assertAccepted();
	}

	@Test
	void stopsAgainstAWall(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.WALL);

		journey.walkFor(FORWARD, 60);
		journey.settle(MovementInput.NONE);

		assertEquals(8.25 - 0.3, journey.movement().position().getX(), EXACT);
		journey.assertAccepted();
	}

	@Test
	void jumpsOntoABlock(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.JUMP);

		journey.walk(MovementInput.builder().forward(true).jump(true).build(), position -> position.isOnGround()
				&& position.getX() > 8.5 && Math.abs(position.getY() - (PhysicsCourse.FLOOR + 1)) < EXACT, 200);
		journey.settle(MovementInput.NONE);

		Position position = journey.movement().position();
		assertEquals(PhysicsCourse.FLOOR + 1, position.getY(), EXACT);
		assertTrue(position.getX() > 8 && position.getX() < 12, "The player left the block at x = " + position.getX());
		journey.assertAccepted();
	}

	@Test
	void sprintJumpsFartherThanItWalks(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.SPRINT);

		journey.walkFor(MovementInput.builder().forward(true).sprint(true).jump(true).build(), 40);
		journey.settle(MovementInput.NONE);

		// Walking covers about 8.6 blocks in 40 ticks, sprint-jumping about 14.
		double travelled = journey.movement().position().getX() - PhysicsCourse.Lane.SPRINT.getX();
		assertTrue(travelled > 11, "The player sprint-jumped only " + travelled + " blocks in 40 ticks");
		journey.assertAccepted();
	}

	@Test
	void sneaksToAnEdgeWithoutFalling(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.EDGE);
		MovementInput sneaking = MovementInput.builder().sneak(true).build();

		journey.walkFor(MovementInput.builder().forward(true).sneak(true).build(), 120);
		journey.settle(sneaking);

		Position position = journey.movement().position();
		assertEquals(PhysicsCourse.FLOOR + 1, position.getY(), EXACT);
		assertTrue(position.getX() > 7.9 && position.getX() < 8.3, "The player stopped at x = " + position.getX());
		journey.assertAccepted();
	}

	@Test
	void standsInWaterAndSwimsUp(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.WATER);
		double[] highest = new double[1];

		journey.walkFor(MovementInput.NONE, 20);
		journey.holdFor(MovementInput.builder().jump(true).build(), 80,
				position -> highest[0] = Math.max(highest[0], position.getY()));
		journey.movement().input(MovementInput.NONE);

		assertTrue(highest[0] > PhysicsCourse.FLOOR + 2, "The player swam up only to y = " + highest[0]);
		journey.verdict().assertAccepted();
	}

	@Test
	void climbsALadderOntoTheColumn(ScenarioContext anvil) {
		Journey journey = start(anvil, PhysicsCourse.Lane.LADDER);

		// A climbing player stands on the column as soon as its box overlaps the top, before its centre is over it.
		journey.walk(FORWARD, position -> position.isOnGround() && position.getX() > 8.3
				&& Math.abs(position.getY() - (PhysicsCourse.FLOOR + 6)) < EXACT, 400);
		journey.settle(MovementInput.NONE);

		Position position = journey.movement().position();
		assertEquals(PhysicsCourse.FLOOR + 6, position.getY(), EXACT);
		assertTrue(position.getX() > 8, "The player did not reach the column's top: x = " + position.getX());
		journey.assertAccepted();
	}

	private Journey start(ScenarioContext anvil, PhysicsCourse.Lane lane) {
		PhysicsCourse course = new PhysicsCourse(anvil, judge());
		course.build();
		SimulatedPlayer player = course.join(joining -> joining.capability(Movement.class).physics(true));
		Movement movement = player.capability(Movement.class);
		PhysicsCourse.Verdict verdict = course.verdict(player);
		Journey journey = new Journey(course, player, movement, verdict);
		journey.settle(MovementInput.NONE);

		course.place(player, lane);
		journey.walk(MovementInput.NONE, position -> Math.abs(position.getX() - lane.getX()) < EXACT
				&& Math.abs(position.getY() - lane.getY()) < EXACT
				&& Math.abs(position.getZ() - lane.getZ()) < EXACT, SETTLE_TICKS);
		movement.look(lane.getYaw(), 0.0F);
		journey.settle(MovementInput.NONE);

		return journey;
	}

	/**
	 * One player's journey on the course.
	 *
	 * @param course the course
	 * @param player the player, whose client physics is on
	 * @param movement the player's movement
	 * @param verdict what the judge says about the player's movement since it joined
	 */
	private record Journey(
			@NotNull PhysicsCourse course,
			@NotNull SimulatedPlayer player,
			@NotNull Movement movement,
			@NotNull PhysicsCourse.Verdict verdict
	) {
		/**
		 * Walks forward off the lane's pillar and lands on the floor.
		 */
		void walkOff() {
			double top = movement.position().getY();
			walk(FORWARD, position -> position.getY() < top - 0.5, 60);
			settle(MovementInput.NONE);
		}

		/**
		 * Holds an input, one tick at a time, until the player's position satisfies a condition.
		 */
		void walk(MovementInput input, Predicate<Position> until, int maximumTicks) {
			movement.input(input);
			for (int tick = 0; tick < maximumTicks; tick++) {
				movement.awaitTicks(1);
				if (until.test(movement.position())) return;
			}

			fail("The player did not get there in " + maximumTicks + " ticks; it is at " + movement.position());
		}

		/**
		 * Holds an input for a number of ticks, showing each tick's position to an observer.
		 */
		void holdFor(MovementInput input, int ticks, Consumer<Position> observer) {
			movement.input(input);
			for (int tick = 0; tick < ticks; tick++) {
				movement.awaitTicks(1);
				observer.accept(movement.position());
			}
		}

		/**
		 * Holds an input for a number of ticks.
		 */
		void walkFor(MovementInput input, int ticks) {
			movement.input(input);
			movement.awaitTicks(ticks);
		}

		/**
		 * Holds an input until the player stands on the ground without moving, then waits until its client has
		 * reported where it stands.
		 */
		void settle(MovementInput input) {
			movement.input(input);
			int still = 0;
			for (int tick = 0; tick < SETTLE_TICKS && still < 5; tick++) {
				movement.awaitTicks(1);
				Motion motion = movement.motion();
				boolean resting = movement.position().isOnGround() && Math.abs(motion.getX()) < 0.003
						&& Math.abs(motion.getZ()) < 0.003;
				still = resting ? still + 1 : 0;
			}
			if (still < 5) fail("The player did not come to rest; it is at " + movement.position());

			movement.awaitTicks(REMINDER_TICKS);
		}

		/**
		 * Expects that the judge refused none of the player's movement and that the server sees the player where its
		 * client is.
		 */
		void assertAccepted() {
			verdict.assertAccepted();
			Position client = movement.position();
			Position server = course.position(player);
			assertEquals(client.getX(), server.getX(), REPORTED, "x on the server");
			assertEquals(client.getY(), server.getY(), REPORTED, "y on the server");
			assertEquals(client.getZ(), server.getZ(), REPORTED, "z on the server");
			assertEquals(client.isOnGround(), server.isOnGround(), "on the ground on the server");
		}
	}
}
