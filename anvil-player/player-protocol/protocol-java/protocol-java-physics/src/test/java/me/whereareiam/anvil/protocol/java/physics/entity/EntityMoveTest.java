package me.whereareiam.anvil.protocol.java.physics.entity;

import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.physics.TestEntity;
import me.whereareiam.anvil.protocol.java.physics.TestWorld;
import me.whereareiam.anvil.protocol.java.physics.geometry.Vector;
import me.whereareiam.anvil.protocol.java.physics.living.LivingSimulation;
import me.whereareiam.anvil.protocol.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.protocol.java.physics.living.attribute.AttributeSupplier;
import me.whereareiam.anvil.protocol.java.physics.living.effect.Effects;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks how an entity moves against the blocks.
 */
class EntityMoveTest {
	@Test
	void movesThroughBlocksWithoutPhysics() {
		LivingSimulation entity = TestEntity.living(walled(), 8.5, 65.0, 8.5);
		entity.body().setNoPhysics(true);
		entity.body().setZza(1.0F);

		for (int tick = 0; tick < 60; tick++) entity.tick();

		assertTrue(entity.body().getPosition().z > 11.0, "the entity passed the wall at " + entity.body().getPosition());
		assertFalse(entity.body().isOnGround());
		assertFalse(entity.body().isHorizontalCollision());
	}

	@Test
	void stopsAtAWall() {
		LivingSimulation entity = TestEntity.living(walled(), 8.5, 65.0, 8.5);
		entity.body().setZza(1.0F);

		for (int tick = 0; tick < 60; tick++) entity.tick();

		assertEquals(10.0 - 0.3F, entity.body().getPosition().z, 1.0E-6);
		assertTrue(entity.body().isHorizontalCollision());
	}

	@Test
	void stepsUpAsHighAsItsStepHeight() {
		TestWorld world = new TestWorld("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone")
				.floor(0, 15, 65, 10, 15, "minecraft:stone").send();
		AttributeMap attributes = new AttributeMap(world.version, AttributeSupplier.living());
		attributes.update(List.of(new AttributeSnapshot("minecraft:step_height", 1.0, List.of())));
		LivingSimulation plain = TestEntity.living(world, 4.5, 65.0, 8.5);
		LivingSimulation stepping = TestEntity.living(world, attributes, new Effects(), new Vector(12.5, 65.0, 8.5));
		plain.body().setZza(1.0F);
		stepping.body().setZza(1.0F);

		for (int tick = 0; tick < 30; tick++) {
			plain.tick();
			stepping.tick();
		}

		assertEquals(65.0, plain.body().getPosition().y);
		assertEquals(66.0, stepping.body().getPosition().y);
	}

	private static @NotNull TestWorld walled() {
		TestWorld world = new TestWorld("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone");
		for (int y = 65; y < 68; y++) world.floor(0, 15, y, 10, 10, "minecraft:stone");
		return world.send();
	}
}
