package me.whereareiam.anvil.edition.java.physics.living;

import me.whereareiam.anvil.edition.java.physics.TestEntity;
import me.whereareiam.anvil.edition.java.physics.TestWorld;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeSupplier;
import me.whereareiam.anvil.edition.java.physics.living.effect.Effects;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Checks the step of a living entity: its input by version, an entity that cannot act, and the steps a subclass
 * steers, as vanilla's subclasses do.
 */
class LivingSimulationTest {
	@Test
	void dampsTheInputAfterTheJumpBefore1_21_5() {
		LivingSimulation older = TestEntity.living(floored("1.21.1"), 8.5, 65.0, 8.5);
		LivingSimulation newer = TestEntity.living(floored("1.21.11"), 8.5, 65.0, 8.5);
		older.body().zza(1.0F);
		newer.body().zza(1.0F);

		older.tick();
		newer.tick();

		assertEquals(0.98F, older.body().zza());
		assertEquals(1.0F, newer.body().zza());
	}

	@Test
	void leavesAnEntityThatCannotActWithoutInputOrJump() {
		TestWorld world = floored("1.21.11");
		LivingBody body = new LivingBody(world.rules(), world.reader(), TestEntity.ZOMBIE, attributes(world),
				new Effects()) {
			@Override
			public boolean immobile() {
				return true;
			}
		};
		body.position(new Vector(8.5, 65.0, 8.5));
		body.zza(1.0F);
		body.jumping(true);

		new LivingSimulation(body).tick();

		assertEquals(0.0F, body.zza());
		assertFalse(body.jumping());
		assertEquals(8.5, body.position().z);
	}

	@Test
	void letsASubclassSetTheInputAndWrapTheTravel() {
		TestWorld world = floored("1.21.11");
		LivingBody body = new LivingBody(world.rules(), world.reader(), TestEntity.ZOMBIE, attributes(world),
				new Effects());
		body.speed(0.1F);
		body.position(new Vector(8.5, 65.0, 8.5));
		List<Vector> travelled = new ArrayList<>();
		LivingSimulation controlled = new LivingSimulation(body) {
			@Override
			protected void serverAiStep() {
				body.xxa(1.0F);
			}

			@Override
			protected void travel(@NotNull Vector input) {
				travelled.add(input);
				super.travel(input);
			}
		};

		controlled.tick();

		assertEquals(List.of(new Vector(1.0F, 0.0, 0.0)), travelled);
	}

	private static @NotNull TestWorld floored(@NotNull String version) {
		return new TestWorld(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
	}

	private static @NotNull AttributeMap attributes(@NotNull TestWorld world) {
		return new AttributeMap(world.version, AttributeSupplier.living());
	}
}
