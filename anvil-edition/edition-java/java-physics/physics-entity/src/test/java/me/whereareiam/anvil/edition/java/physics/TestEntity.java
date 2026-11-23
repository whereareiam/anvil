package me.whereareiam.anvil.edition.java.physics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.physics.entity.EntityDimensions;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.living.LivingBody;
import me.whereareiam.anvil.edition.java.physics.living.LivingSimulation;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeSupplier;
import me.whereareiam.anvil.edition.java.physics.living.effect.Effects;
import org.jetbrains.annotations.NotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain living entities in a {@link TestWorld}: no player, only what {@code LivingEntity} gives every living entity,
 * with the dimensions of a zombie and a speed its controls set to {@code 0.1F}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestEntity {
	/**
	 * The dimensions of a zombie.
	 */
	public static final EntityDimensions ZOMBIE = new EntityDimensions(0.6F, 1.95F, 1.74F);

	/**
	 * Starts simulating a living entity at rest at a position of a world.
	 */
	public static @NotNull LivingSimulation living(@NotNull TestWorld world, double x, double y, double z) {
		AttributeMap attributes = new AttributeMap(world.version, AttributeSupplier.living());
		return living(world, attributes, new Effects(), new Vector(x, y, z));
	}

	/**
	 * Starts simulating a living entity with the given attributes and effects at rest at a position of a world.
	 */
	public static @NotNull LivingSimulation living(
			@NotNull TestWorld world,
			@NotNull AttributeMap attributes,
			@NotNull Effects effects,
			@NotNull Vector at
	) {
		LivingBody body = new LivingBody(world.rules(), world.reader(), ZOMBIE, attributes, effects);
		body.speed(0.1F);
		body.position(at);
		return new LivingSimulation(body);
	}

	/**
	 * Ticks an entity until it has stood on the ground for a few ticks.
	 */
	public static void settle(@NotNull LivingSimulation entity) {
		for (int tick = 0; tick < 5; tick++) entity.tick();

		assertTrue(entity.body().onGround(), "the entity stands on the ground");
	}
}
