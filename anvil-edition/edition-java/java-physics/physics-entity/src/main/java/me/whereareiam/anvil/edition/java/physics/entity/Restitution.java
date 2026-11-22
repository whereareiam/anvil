package me.whereareiam.anvil.edition.java.physics.entity;

import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.BlockType;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import me.whereareiam.anvil.edition.java.physics.geometry.Axis;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * What a collision leaves of an entity's velocity from 26.2 ({@code Entity.restituteMovementAfterCollisions}), which
 * replaces stopping it at a wall and a block's {@code updateEntityMovementAfterFallOn}: each blocked horizontal
 * component is reversed and scaled by the entity's bounciness, and a vertical collision keeps, reversed, the share of a
 * bounce: on landing faster than gravity, the larger of the entity's and the landed block's bounciness, with the gravity
 * and drag of the part of the tick it fell after the collision; no bounce for an entity that suppresses it or on a block
 * that does. From 26.3 a landing must be faster than gravity, not as fast.
 *
 * <p>A bounce also calls the block's {@code bounceOn} and emits a bounce game event; neither moves the entity: the
 * block's {@code bounceOn} does nothing, and the one block that overrides it, 26.3's shelf mushroom
 * ({@code ShelfMushroomBlock}), only plays its bounce sound, and the client's level ignores game events.</p>
 */
final class Restitution {
	private static final String SUPPRESSES_BOUNCE = "minecraft:suppresses_bounce";

	private final @NotNull EntityBody body;
	private final @NotNull Bounciness bounciness;

	/**
	 * Creates the restitution of an entity's collisions.
	 *
	 * @param body the entity
	 * @param bounciness what the entity brings to a bounce
	 */
	Restitution(@NotNull EntityBody body, @NotNull Bounciness bounciness) {
		this.body = body;
		this.bounciness = bounciness;
	}

	/**
	 * Restitutes the entity's velocity after a movement, where it moved vertically and collided so, or collided
	 * horizontally.
	 *
	 * @param landed the block the entity's effects come from ({@code getOnPosLegacy})
	 * @param movement the movement the entity asked for
	 * @param collided the movement the blocks allowed
	 */
	void apply(@NotNull BlockState landed, @NotNull Vector movement, @NotNull Vector collided) {
		boolean collidedX = !EntityMove.equal(movement.x, collided.x);
		boolean collidedZ = !EntityMove.equal(movement.z, collided.z);
		boolean vertical = movement.y != collided.y;
		boolean movedVertically = Math.abs(movement.y) > 0.0;
		if (!(movedVertically && vertical || collidedX || collidedZ)) return;

		double restitution = body.isSuppressingBounce() ? 0.0 : bounciness.entityBounciness();
		Vector current = body.getDeltaMovement();
		Vector after = current;
		if (collidedX) after = after.with(Axis.X, -current.x * restitution);
		if (collidedZ) after = after.with(Axis.Z, -current.z * restitution);
		if (vertical) after = after.with(Axis.Y, vertical(landed, collided, current, restitution, movement.y < 0.0));
		body.setDeltaMovement(after);
	}

	/**
	 * The vertical speed a vertical collision leaves: none without a bounce, else the reversed speed, less the gravity of
	 * the part of the tick after the collision, times its drag and the bounciness.
	 */
	private double vertical(
			@NotNull BlockState landed,
			@NotNull Vector collided,
			@NotNull Vector current,
			double restitution,
			boolean below
	) {
		double bounce = below ? landingBounce(landed, current, restitution) : restitution;
		if (!(bounce > 0.0)) return (0.0 - current.y) * 1.0 * bounce;

		double portion = collided.y / current.y;
		double gravityCompensation = portion * bounciness.effectiveGravity();
		double drag = 1.0 + portion * ((double) bounciness.airDrag() - 1.0);
		return (gravityCompensation - current.y) * drag * bounce;
	}

	/**
	 * The bounce of a landing: the larger of the entity's and the block's bounciness ({@code getBounceRestitution}) when
	 * it lands faster than gravity and neither it nor the block suppresses the bounce, which a block in the
	 * {@code minecraft:suppresses_bounce} tag does.
	 */
	private double landingBounce(@NotNull BlockState landed, @NotNull Vector current, double restitution) {
		double gravity = bounciness.effectiveGravity();
		boolean slow = body.has(EntityBehaviour.BOUNCE_ABOVE_GRAVITY) ? -current.y <= gravity : -current.y < gravity;
		BlockType block = landed.getBlock();
		boolean suppressed = body.isSuppressingBounce() || block.hasTag(SUPPRESSES_BOUNCE);
		if (slow || suppressed) return 0.0;

		return Math.max(restitution, bounciness.blockBounciness((float) block.getMovement().getBounceFactor()));
	}
}
