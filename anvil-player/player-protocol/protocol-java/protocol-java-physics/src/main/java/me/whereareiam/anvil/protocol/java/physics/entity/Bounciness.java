package me.whereareiam.anvil.protocol.java.physics.entity;

/**
 * What an entity brings to the restitution of its movement after a collision, from 26.2
 * ({@code Entity.restituteMovementAfterCollisions}): its own bounciness, how much of a block's bounce it takes, its
 * gravity and its air drag. {@link #ENTITY} gives {@code Entity}'s defaults; a living entity overrides them as
 * {@code LivingEntity} does.
 */
public interface Bounciness {
	/**
	 * {@code Entity}'s defaults: no bounciness of its own, {@code 0.8F} of a block's bounce, no gravity and an air drag
	 * of {@code 0.98F}.
	 */
	Bounciness ENTITY = new Bounciness() {
		@Override
		public double entityBounciness() {
			return 0.0;
		}

		@Override
		public double blockBounciness(float restitution) {
			return restitution * 0.8F;
		}

		@Override
		public double effectiveGravity() {
			return 0.0;
		}

		@Override
		public float airDrag() {
			return 0.98F;
		}
	};

	/**
	 * The entity's own bounciness ({@code getEntityBounciness}).
	 *
	 * @return the share of its speed it keeps, reversed, when it hits a block
	 */
	double entityBounciness();

	/**
	 * How much of a block's bounce the entity takes ({@code getBlockBounciness}).
	 *
	 * @param restitution the block's bounce restitution
	 * @return the bounciness the block gives the entity
	 */
	double blockBounciness(float restitution);

	/**
	 * The gravity the entity falls with now ({@code getEffectiveGravity}).
	 *
	 * @return the gravity
	 */
	double effectiveGravity();

	/**
	 * The factor of the entity's vertical speed the air leaves it each tick ({@code getAirDrag}).
	 *
	 * @return the air drag
	 */
	float airDrag();
}
