package me.whereareiam.anvil.capability.movement.java.physics.world;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.PlayerSimulation;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the collision shapes the blocks give the player.
 */
class CollisionsTest {
	@Test
	void standsOnScaffoldingAndSinksThroughItWhileSneaking() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 60, 0, 15, "minecraft:stone");
		for (int y = 61; y < 65; y++) client.set(8, y, 8, "minecraft:scaffolding");
		PlayerSimulation player = client.send().simulation(8.5, 66.0, 8.5);

		for (int tick = 0; tick < 20; tick++) player.tick(MovementInput.NONE);
		assertEquals(65.0, player.body().getPosition().y);
		assertTrue(player.body().isOnGround());

		for (int tick = 0; tick < 80; tick++) player.tick(MovementInput.builder().sneak(true).build());
		assertEquals(61.0, player.body().getPosition().y, 1.0E-6);
	}

	@Test
	void sinksIntoPowderSnowUnlessItFellFarOntoIt() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 60, 0, 15, "minecraft:stone")
				.floor(0, 15, 61, 0, 15, "minecraft:powder_snow").send();
		PlayerSimulation stepping = client.simulation(4.5, 62.0, 4.5);
		PlayerSimulation falling = client.simulation(12.5, 70.0, 12.5);
		boolean landedOnTheSnow = false;

		for (int tick = 0; tick < 60; tick++) {
			stepping.tick(MovementInput.NONE);
			falling.tick(MovementInput.NONE);
			landedOnTheSnow |= falling.body().isOnGround() && falling.body().getPosition().y == 61.0 + 0.9F;
		}

		assertTrue(stepping.body().getPosition().y < 62.0, "the player sank to " + stepping.body().getPosition());
		assertTrue(landedOnTheSnow, "the fall landed on the snow's falling shape");
	}
}
