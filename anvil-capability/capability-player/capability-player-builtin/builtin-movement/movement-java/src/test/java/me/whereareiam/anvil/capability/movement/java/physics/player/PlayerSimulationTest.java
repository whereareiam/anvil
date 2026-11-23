package me.whereareiam.anvil.capability.movement.java.physics.player;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks ticks of the player against values computed by hand from vanilla's arithmetic.
 */
class PlayerSimulationTest {
	private static final MovementInput FORWARD = MovementInput.builder().forward(true).build();
	private static final MovementInput SNEAK_FORWARD = MovementInput.builder().forward(true).sneak(true).build();
	private static final MovementInput JUMP = MovementInput.builder().jump(true).build();

	@ParameterizedTest
	@ValueSource(strings = {"1.16.5", "1.20.6", "1.21.1", "1.21.11", "26.1.2"})
	void fallsFromRestWithGravityAndDrag(@NotNull String version) {
		PlayerSimulation player = new TestClient(version).join().load(0, 0).send().simulation(0.5, 100.0, 0.5);

		double y = 100.0;
		double velocity = 0.0;
		for (int tick = 0; tick < 30; tick++) {
			player.tick(MovementInput.NONE);
			if (Math.abs(velocity) < 0.003) velocity = 0.0;
			y += velocity;
			velocity = (velocity - 0.08) * (double) 0.98F;

			assertEquals(velocity, player.body().deltaMovement().y, "velocity after tick " + (tick + 1));
			assertEquals(y, player.body().position().y, "height after tick " + (tick + 1));
		}
		assertFalse(player.body().onGround());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.16.5", "1.20.6", "1.21.11"})
	void landsOnAFullBlockAndKeepsPressingIntoIt(@NotNull String version) {
		TestClient client = new TestClient(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerSimulation player = client.simulation(8.5, 68.0, 8.5);

		for (int tick = 0; tick < 40; tick++) player.tick(MovementInput.NONE);

		assertEquals(65.0, player.body().position().y);
		assertTrue(player.body().onGround());
		assertEquals(-0.0784000015258789, player.body().deltaMovement().y);
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.1", "1.21.11"})
	void walksOnStoneAtVanillasWalkingSpeed(@NotNull String version) {
		assertWalksAtTheFrictionsSpeed(version, "minecraft:stone", 0.6F);
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void slidesFasterOnIce(@NotNull String version) {
		assertWalksAtTheFrictionsSpeed(version, "minecraft:ice", 0.98F);
	}

	private static void assertWalksAtTheFrictionsSpeed(@NotNull String version, @NotNull String block, float friction) {
		TestClient client = new TestClient(version).join().load(0, 0).load(0, 1).load(0, 2)
				.floor(0, 15, 64, 0, 47, block).send();
		PlayerSimulation player = client.simulation(8.5, 65.0, 2.5);
		settle(player);

		double z = player.body().position().z;
		double velocity = 0.0;
		float acceleration = 0.1F * (0.21600002F / (friction * friction * friction));
		double drag = friction * 0.91F;
		for (int tick = 0; tick < 60; tick++) {
			player.tick(FORWARD);
			if (Math.abs(velocity) < 0.003) velocity = 0.0;
			velocity += 0.98F * (double) acceleration;
			z += velocity;
			velocity *= drag;

			assertEquals(z, player.body().position().z, "position after tick " + (tick + 1));
			assertEquals(8.5, player.body().position().x);
			assertTrue(player.body().onGround());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void stepsUpOntoASlab(@NotNull String version) {
		TestClient client = new TestClient(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone")
				.floor(0, 15, 65, 6, 15, "minecraft:stone_slab").send();
		PlayerSimulation player = client.simulation(8.5, 65.0, 2.5);
		settle(player);

		for (int tick = 0; tick < 30 && player.body().position().y < 65.5; tick++) player.tick(FORWARD);

		assertEquals(65.5, player.body().position().y);
		assertTrue(player.body().position().z > 5.7, "the player stands on the slab at " + player.body().position());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.18.2", "1.20.6", "1.21.11"})
	void stopsAtTheEdgeWhileSneaking(@NotNull String version) {
		PlayerSimulation player = new TestClient(version).join().load(0, 0).set(8, 64, 8, "minecraft:stone").send()
				.simulation(8.5, 65.0, 8.5);
		settle(player);

		for (int tick = 0; tick < 60; tick++) player.tick(SNEAK_FORWARD);

		assertEquals(65.0, player.body().position().y);
		assertTrue(player.body().onGround());
		double z = player.body().position().z;
		assertTrue(z < 9.3 && z > 9.2, "the player stays on the block at " + z);
		assertEquals(1.5, player.body().box().maxY - player.body().box().minY, 1.0E-6);
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void walksOffTheEdgeWithoutSneaking(@NotNull String version) {
		PlayerSimulation player = new TestClient(version).join().load(0, 0).set(8, 64, 8, "minecraft:stone").send()
				.simulation(8.5, 65.0, 8.5);
		settle(player);

		for (int tick = 0; tick < 30; tick++) player.tick(FORWARD);

		assertTrue(player.body().position().y < 64.0, "the player fell to " + player.body().position());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.16.5", "1.21.11"})
	void jumpsWithTheJumpPowerAndWaitsBeforeJumpingAgain(@NotNull String version) {
		PlayerSimulation player = new TestClient(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone")
				.send().simulation(8.5, 65.0, 8.5);
		settle(player);

		player.tick(JUMP);

		assertEquals((0.42F - 0.08) * (double) 0.98F, player.body().deltaMovement().y);
		assertEquals(65.0 + 0.42F, player.body().position().y);
		int landed = 0;
		for (int tick = 0; tick < 40 && landed == 0; tick++) {
			player.tick(JUMP);
			if (player.body().onGround()) landed = tick + 1;
		}
		assertTrue(landed > 0, "the player landed");
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void bouncesOnSlime(@NotNull String version) {
		PlayerSimulation player = new TestClient(version).join().load(0, 0)
				.floor(0, 15, 64, 0, 15, "minecraft:slime_block").send().simulation(8.5, 70.0, 8.5);
		double fastest = 0.0;
		boolean bounced = false;
		for (int tick = 0; tick < 40 && !bounced; tick++) {
			player.tick(MovementInput.NONE);
			fastest = Math.min(fastest, player.body().deltaMovement().y);
			bounced = player.body().deltaMovement().y > 0.0;
		}

		assertTrue(bounced, "the player bounced");
		assertTrue(player.body().deltaMovement().y > -fastest * 0.8, "it kept most of its fall speed");
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void sinksSlowlyThroughCobwebs(@NotNull String version) {
		TestClient client = new TestClient(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone");
		for (int y = 65; y < 70; y++) client.set(8, y, 8, "minecraft:cobweb");
		PlayerSimulation player = client.send().simulation(8.5, 69.0, 8.5);

		for (int tick = 0; tick < 20; tick++) player.tick(MovementInput.NONE);

		assertTrue(player.body().position().y > 68.0, "the cobweb held the player at " + player.body().position());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void climbsALadderWhileJumping(@NotNull String version) {
		TestClient client = new TestClient(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone")
				.floor(0, 15, 65, 10, 15, "minecraft:stone");
		for (int y = 65; y < 75; y++) client.set(8, y, 9, "minecraft:ladder");
		PlayerSimulation player = client.send().simulation(8.5, 65.0, 9.5);
		settle(player);

		for (int tick = 0; tick < 20; tick++) player.tick(MovementInput.builder().jump(true).forward(true).build());

		assertTrue(player.body().position().y > 67.0, "the player climbed to " + player.body().position());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void swimsUpInWaterWhileJumping(@NotNull String version) {
		TestClient client = new TestClient(version).join().load(0, 0).floor(0, 15, 60, 0, 15, "minecraft:stone");
		for (int y = 61; y < 66; y++) client.floor(0, 15, y, 0, 15, "minecraft:water");
		PlayerSimulation player = client.send().simulation(8.5, 61.0, 8.5);

		for (int tick = 0; tick < 5; tick++) player.tick(MovementInput.NONE);
		double sunk = player.body().position().y;
		for (int tick = 0; tick < 20; tick++) player.tick(JUMP);

		assertTrue(player.body().position().y > sunk + 1.0, "the player swam up from " + sunk);
		assertTrue(Math.abs(player.body().deltaMovement().y) < 0.2, "water slowed it");
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void sprintsFasterAndBoostsItsJump(@NotNull String version) {
		TestClient client = new TestClient(version).join().load(0, 0).load(0, 1).load(0, 2)
				.floor(0, 15, 64, 0, 47, "minecraft:stone").send();
		PlayerSimulation walker = client.simulation(4.5, 65.0, 2.5);
		PlayerSimulation sprinter = client.simulation(12.5, 65.0, 2.5);
		settle(walker);
		settle(sprinter);

		MovementInput sprint = MovementInput.builder().forward(true).sprint(true).build();
		for (int tick = 0; tick < 40; tick++) {
			walker.tick(FORWARD);
			sprinter.tick(sprint);
		}

		assertTrue(sprinter.body().sprinting());
		assertFalse(walker.body().sprinting());
		double walked = walker.body().position().z - 2.5;
		double sprinted = sprinter.body().position().z - 2.5;
		assertTrue(sprinted > walked * 1.25 && sprinted < walked * 1.35, "sprinted " + sprinted + " against " + walked);

		double before = sprinter.body().position().z;
		sprinter.tick(MovementInput.builder().forward(true).sprint(true).jump(true).build());
		assertTrue(sprinter.body().position().z - before > 0.4, "the sprint jump boosted the player");
	}

	static void settle(@NotNull PlayerSimulation player) {
		for (int tick = 0; tick < 5; tick++) player.tick(MovementInput.NONE);

		assertTrue(player.body().onGround(), "the player stands on the ground");
	}
}
