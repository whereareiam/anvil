package me.whereareiam.anvil.edition.java.physics.living;

import me.whereareiam.anvil.edition.java.physics.TestEntity;
import me.whereareiam.anvil.edition.java.physics.TestWorld;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeSupplier;
import me.whereareiam.anvil.edition.java.physics.living.effect.Effects;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the travel of a living entity through the air with its effects and attributes, and on the ground.
 */
class TravelTest {
	@ParameterizedTest
	@ValueSource(strings = {"1.16.5", "1.20.6", "1.21.1", "1.21.11", "26.1.2"})
	void fallsFromRestWithGravityAndDrag(@NotNull String version) {
		LivingSimulation entity = TestEntity.living(new TestWorld(version).join().load(0, 0).send(), 0.5, 100.0, 0.5);

		double y = 100.0;
		double velocity = 0.0;
		for (int tick = 0; tick < 30; tick++) {
			entity.tick();
			if (Math.abs(velocity) < 0.003) velocity = 0.0;
			y += velocity;
			velocity = (velocity - 0.08) * (double) 0.98F;

			assertEquals(velocity, entity.body().deltaMovement().y, "velocity after tick " + (tick + 1));
			assertEquals(y, entity.body().position().y, "height after tick " + (tick + 1));
		}
		assertFalse(entity.body().onGround());
	}

	@Test
	void walksAtItsSpeedScaledByTheFrictionOfTheBlockBelow() {
		TestWorld world = new TestWorld("1.21.11").join().load(0, 0).load(0, 1).load(0, 2)
				.floor(0, 15, 64, 0, 47, "minecraft:ice").send();
		LivingSimulation entity = TestEntity.living(world, 8.5, 65.0, 2.5);
		TestEntity.settle(entity);
		entity.body().zza(1.0F);

		double z = entity.body().position().z;
		double velocity = 0.0;
		float acceleration = 0.1F * (0.21600002F / (0.98F * 0.98F * 0.98F));
		for (int tick = 0; tick < 40; tick++) {
			entity.tick();
			if (Math.abs(velocity) < 0.003) velocity = 0.0;
			velocity += acceleration;
			z += velocity;
			velocity *= 0.98F * 0.91F;

			assertEquals(z, entity.body().position().z, "position after tick " + (tick + 1));
		}
	}

	@Test
	void risesWithLevitationAndStopsCountingItsFall() {
		Effects effects = new Effects();
		effects.add("minecraft:levitation", 0);
		LivingSimulation entity = inTheAir(effects, null);

		entity.tick();
		assertEquals((0.0 + (0.05 * 1 - 0.0) * 0.2) * 0.98F, entity.body().deltaMovement().y);
		for (int tick = 0; tick < 40; tick++) entity.tick();

		assertTrue(entity.body().position().y > 101.0, "it rose to " + entity.body().position());
		assertTrue(entity.body().deltaMovement().y < 0.05);
		assertEquals(0.0, entity.body().fallDistance());
	}

	@Test
	void fallsSlowlyWithSlowFalling() {
		Effects effects = new Effects();
		effects.add("minecraft:slow_falling", 0);
		LivingSimulation entity = inTheAir(effects, null);

		entity.tick();
		entity.tick();

		assertEquals(((0.0 - 0.01) * 0.98F - 0.01) * 0.98F, entity.body().deltaMovement().y);
	}

	@Test
	void fallsWithTheGravityAttribute() {
		LivingSimulation entity = inTheAir(new Effects(), new AttributeSnapshot("minecraft:gravity", 0.04, List.of()));

		entity.tick();

		assertEquals((0.0 - 0.04) * 0.98F, entity.body().deltaMovement().y);
	}

	private static @NotNull LivingSimulation inTheAir(@NotNull Effects effects, @Nullable AttributeSnapshot attribute) {
		TestWorld world = new TestWorld("1.21.11").join().load(0, 0).send();
		AttributeMap attributes = new AttributeMap(world.version, AttributeSupplier.living());
		if (attribute != null) attributes.update(List.of(attribute));
		return TestEntity.living(world, attributes, effects, new Vector(8.5, 100.0, 8.5));
	}
}
