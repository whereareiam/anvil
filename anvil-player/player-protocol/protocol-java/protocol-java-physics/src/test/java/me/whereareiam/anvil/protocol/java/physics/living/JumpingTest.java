package me.whereareiam.anvil.protocol.java.physics.living;

import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.physics.TestEntity;
import me.whereareiam.anvil.protocol.java.physics.TestWorld;
import me.whereareiam.anvil.protocol.java.physics.geometry.Vector;
import me.whereareiam.anvil.protocol.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.protocol.java.physics.living.attribute.AttributeSupplier;
import me.whereareiam.anvil.protocol.java.physics.living.effect.Effects;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks a living entity's jumps: their power, from the jump strength, jump boost and the block, and their delay.
 */
class JumpingTest {
	@ParameterizedTest
	@ValueSource(strings = {"1.16.5", "1.20.6", "1.21.11"})
	void jumpsHigherWithJumpBoostUntilItEnds(@NotNull String version) {
		TestWorld world = floored(version);
		Effects effects = new Effects();
		effects.add("minecraft:jump_boost", 1);
		LivingSimulation entity = TestEntity.living(world, living(world), effects, new Vector(8.5, 65.0, 8.5));
		TestEntity.settle(entity);

		jump(entity);

		float power = 0.42F * 1.0F * 1.0F + 0.1F * (1 + 1.0F);
		assertEquals(65.0 + power, entity.body().getPosition().y);
		for (int tick = 0; tick < 40 && !entity.body().isOnGround(); tick++) entity.tick();
		effects.remove("minecraft:jump_boost");
		for (int tick = 0; tick < 10; tick++) entity.tick();
		jump(entity);
		assertEquals(65.0 + 0.42F, entity.body().getPosition().y);
	}

	@Test
	void jumpsWithItsJumpStrength() {
		TestWorld world = floored("1.21.11");
		AttributeMap attributes = living(world);
		attributes.update(List.of(new AttributeSnapshot("minecraft:jump_strength", 1.0, List.of())));
		LivingSimulation entity = TestEntity.living(world, attributes, new Effects(), new Vector(8.5, 65.0, 8.5));
		TestEntity.settle(entity);

		jump(entity);

		assertEquals(66.0, entity.body().getPosition().y);
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.16.5", "1.21.11"})
	void waitsTenTicksBeforeJumpingAgainWhileItKeepsJumping(@NotNull String version) {
		LivingSimulation entity = TestEntity.living(floored(version), 8.5, 65.0, 8.5);
		TestEntity.settle(entity);
		entity.body().setJumping(true);

		entity.tick();
		assertEquals((0.42F - 0.08) * (double) 0.98F, entity.body().getDeltaMovement().y);
		int landed = 0;
		for (int tick = 0; tick < 40 && landed == 0; tick++) {
			entity.tick();
			if (entity.body().isOnGround()) landed = tick + 1;
		}

		assertTrue(landed > 0, "the entity landed");
		entity.tick();
		assertTrue(entity.body().getDeltaMovement().y > 0.0, "it jumped again as the delay had passed in the air");
	}

	private static void jump(@NotNull LivingSimulation entity) {
		entity.body().setJumping(true);
		entity.tick();
		entity.body().setJumping(false);
	}

	private static @NotNull TestWorld floored(@NotNull String version) {
		return new TestWorld(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
	}

	private static @NotNull AttributeMap living(@NotNull TestWorld world) {
		return new AttributeMap(world.version, AttributeSupplier.living());
	}
}
