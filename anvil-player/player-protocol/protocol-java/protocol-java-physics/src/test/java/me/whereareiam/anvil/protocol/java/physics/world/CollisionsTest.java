package me.whereareiam.anvil.protocol.java.physics.world;

import me.whereareiam.anvil.protocol.java.block.model.collision.BlockOffset;
import me.whereareiam.anvil.protocol.java.physics.TestEntity;
import me.whereareiam.anvil.protocol.java.physics.TestWorld;
import me.whereareiam.anvil.protocol.java.physics.living.LivingSimulation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the collision shapes the blocks give an entity.
 */
class CollisionsTest {
	@Test
	void standsOnScaffoldingAndSinksThroughItWhileItsShiftKeyIsDown() {
		TestWorld world = new TestWorld("1.21.11").join().load(0, 0).floor(0, 15, 60, 0, 15, "minecraft:stone");
		for (int y = 61; y < 65; y++) world.set(8, y, 8, "minecraft:scaffolding");
		LivingSimulation entity = TestEntity.living(world.send(), 8.5, 66.0, 8.5);

		for (int tick = 0; tick < 20; tick++) entity.tick();
		assertEquals(65.0, entity.body().getPosition().y);
		assertTrue(entity.body().isOnGround());

		entity.body().setShiftKeyDown(true);
		for (int tick = 0; tick < 80; tick++) entity.tick();
		assertEquals(61.0, entity.body().getPosition().y, 1.0E-6);
	}

	@Test
	void sinksIntoPowderSnowUnlessItFellFarOntoIt() {
		TestWorld world = new TestWorld("1.21.11").join().load(0, 0).floor(0, 15, 60, 0, 15, "minecraft:stone")
				.floor(0, 15, 61, 0, 15, "minecraft:powder_snow").send();
		LivingSimulation stepping = TestEntity.living(world, 4.5, 62.0, 4.5);
		LivingSimulation falling = TestEntity.living(world, 12.5, 70.0, 12.5);
		boolean landedOnTheSnow = false;

		for (int tick = 0; tick < 60; tick++) {
			stepping.tick();
			falling.tick();
			landedOnTheSnow |= falling.body().isOnGround() && falling.body().getPosition().y == 61.0 + 0.9F;
		}

		assertTrue(stepping.body().getPosition().y < 62.0, "the entity sank to " + stepping.body().getPosition());
		assertTrue(landedOnTheSnow, "the fall landed on the snow's falling shape");
	}

	@Test
	void walksIntoBambooWhereItsPositionMovedIt() {
		TestWorld world = new TestWorld("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone")
				.set(8, 65, 8, "minecraft:bamboo").send();
		LivingSimulation entity = TestEntity.living(world, 8.5, 65.0, 4.5);
		entity.body().setZza(1.0F);

		for (int tick = 0; tick < 60; tick++) entity.tick();

		double face = 8.0 + (0.40625 + PositionOffset.at(bamboo(world), 8, 8).z);
		assertEquals(face - 0.3F, entity.body().getPosition().z, 1.0E-6);
		assertTrue(entity.body().isHorizontalCollision());
	}

	private static @NotNull BlockOffset bamboo(@NotNull TestWorld world) {
		return Objects.requireNonNull(world.registry.block("minecraft:bamboo").orElseThrow().getOffset());
	}
}
