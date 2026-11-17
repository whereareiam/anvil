package me.whereareiam.anvil.protocol.java.physics.entity;

import me.whereareiam.anvil.protocol.java.physics.EntityBehaviour;
import me.whereareiam.anvil.protocol.java.physics.geometry.Box;
import me.whereareiam.anvil.protocol.java.physics.geometry.Vector;
import me.whereareiam.anvil.protocol.java.physics.world.Collisions;
import me.whereareiam.anvil.protocol.java.physics.world.EntityContext;
import me.whereareiam.anvil.protocol.java.physics.world.shape.PlacedShape;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code Entity.move} for an entity's own movement: a block it is stuck in scales the movement once, the entity may
 * back off an edge ({@link EntityBody#maybeBackOffFromEdge}), the movement collides with the blocks and steps up onto
 * what stops it horizontally ({@code Entity.collide}), and then the collision flags, the block stood on, the fall
 * distance, landing, the blocks inside the entity before 1.21.2, and the speed factor of the block below follow. An
 * entity without physics moves through blocks ({@code noPhysics}).
 */
public final class EntityMove {
	private final @NotNull EntityBody body;
	private final @NotNull Collisions collisions;
	private final @NotNull BlockEffects effects;
	private final @NotNull FluidState fluids;

	/**
	 * Creates the movement of an entity.
	 *
	 * @param body the entity
	 * @param effects what the blocks do to it
	 * @param fluids how it is in fluids
	 */
	public EntityMove(@NotNull EntityBody body, @NotNull BlockEffects effects, @NotNull FluidState fluids) {
		this.body = body;
		this.collisions = new Collisions(body.getLevel());
		this.effects = effects;
		this.fluids = fluids;
	}

	/**
	 * Moves the entity by a movement.
	 *
	 * @param requested the movement, usually the entity's velocity
	 */
	public void move(@NotNull Vector requested) {
		if (body.isNoPhysics()) {
			body.setPosition(body.getPosition().add(requested));
			body.setHorizontalCollision(false);
			body.setMinorHorizontalCollision(false);
			return;
		}

		Vector movement = body.maybeBackOffFromEdge(unstuck(requested));
		Vector collided = collide(movement);
		place(movement, collided);
		land(movement, collided);
		float factor = effects.speedFactor();
		body.setDeltaMovement(body.getDeltaMovement().multiply(factor, 1.0, factor));
	}

	private @NotNull Vector unstuck(@NotNull Vector movement) {
		Vector stuck = body.getStuckSpeedMultiplier();
		if (!(stuck.lengthSqr() > 1.0E-7)) return movement;

		body.setStuckSpeedMultiplier(Vector.ZERO);
		body.setDeltaMovement(Vector.ZERO);
		return movement.multiply(stuck);
	}

	private void place(@NotNull Vector movement, @NotNull Vector collided) {
		double lengthSqr = collided.lengthSqr();
		boolean tiny = body.has(EntityBehaviour.PATH_BLOCK_EFFECTS) && movement.lengthSqr() - lengthSqr < 1.0E-7;
		if (!(lengthSqr > 1.0E-7) && !tiny) return;

		if (body.has(EntityBehaviour.BOX_DRIVEN_POSITION)) body.moveBox(collided);
		else body.setPosition(body.getPosition().add(collided));
	}

	private void land(@NotNull Vector movement, @NotNull Vector collided) {
		boolean collidedX = !equal(movement.x, collided.x);
		boolean collidedZ = !equal(movement.z, collided.z);
		boolean vertical = movement.y != collided.y;
		body.setHorizontalCollision(collidedX || collidedZ);
		body.setOnGround(vertical && movement.y < 0.0);
		if (body.has(EntityBehaviour.SUPPORTING_BLOCK)) effects.checkSupportingBlock(collided);
		body.setMinorHorizontalCollision(body.isHorizontalCollision() && body.isHorizontalCollisionMinor(collided));

		int[] landed = effects.onPos(0.2F);
		checkFallDamage(collided.y);
		Vector delta = body.getDeltaMovement();
		if (body.isHorizontalCollision())
			body.setDeltaMovement(new Vector(collidedX ? 0.0 : delta.x, delta.y, collidedZ ? 0.0 : delta.z));
		if (vertical) effects.fallOn(landed);
		if (body.has(EntityBehaviour.PATH_BLOCK_EFFECTS)) return;

		if (body.isOnGround()) effects.stepOn(landed);
		effects.insideDuringMove();
	}

	private static boolean equal(double first, double second) {
		return Math.abs(second - first) < 1.0E-5F;
	}

	/**
	 * {@code Entity.checkFallDamage} and {@code LivingEntity.checkFallDamage}: updates the water state out of water and
	 * the fall distance, which landing resets; from 1.21.5 the distance counts before the landing resets it, and not in
	 * water.
	 */
	private void checkFallDamage(double movedY) {
		if (!fluids.isTouchingWater()) fluids.updateWater();

		if (body.has(EntityBehaviour.DOUBLE_FALL_DISTANCE)) {
			if (!fluids.isTouchingWater() && movedY < 0.0) body.setFallDistance(body.getFallDistance() - (float) movedY);
			if (body.isOnGround()) body.resetFallDistance();
			return;
		}

		if (body.isOnGround()) {
			body.resetFallDistance();
			return;
		}
		if (movedY < 0.0) body.setFallDistance((float) body.getFallDistance() - (float) movedY);
	}

	/**
	 * {@code Entity.collide}: moves the entity against the blocks and steps up onto what stops it horizontally.
	 */
	private @NotNull Vector collide(@NotNull Vector movement) {
		Box box = body.getBox();
		EntityContext context = body.context();
		Vector collided = movement.lengthSqr() == 0.0 ? movement : collisions.collideBoundingBox(movement, box, context);
		boolean landing = movement.y != collided.y && movement.y < 0.0;
		boolean blocked = movement.x != collided.x || movement.z != collided.z;
		float stepHeight = body.maxUpStep();
		if (!(stepHeight > 0.0F) || !(landing || body.isOnGround()) || !blocked) return collided;
		if (body.has(EntityBehaviour.CANDIDATE_STEP_HEIGHTS)) return stepUp(movement, collided, landing, stepHeight);

		return legacyStepUp(movement, collided, stepHeight);
	}

	/**
	 * Stepping up from 1.21: the candidate heights of the shapes around, lowest first, until one gets further.
	 */
	private @NotNull Vector stepUp(@NotNull Vector movement, @NotNull Vector collided, boolean landing, float stepHeight) {
		Box box = body.getBox();
		Box base = landing ? box.move(0.0, collided.y, 0.0) : box;
		Box reach = base.expandTowards(movement.x, stepHeight, movement.z);
		if (!landing) reach = reach.expandTowards(0.0, -1.0E-5F, 0.0);

		List<PlacedShape> shapes = collisions.blockCollisions(reach, body.context());
		for (float height : Collisions.candidateStepHeights(base, shapes, stepHeight, (float) collided.y)) {
			Vector stepped = Collisions.collideWithShapes(new Vector(movement.x, height, movement.z), base, shapes);
			if (stepped.horizontalDistanceSqr() > collided.horizontalDistanceSqr())
				return stepped.subtract(0.0, box.minY - base.minY, 0.0);
		}

		return collided;
	}

	/**
	 * Stepping up before 1.21: the full step height, or the height the box can rise and move across, whichever gets
	 * further, set back down onto what is below.
	 */
	private @NotNull Vector legacyStepUp(@NotNull Vector movement, @NotNull Vector collided, float stepHeight) {
		Box box = body.getBox();
		EntityContext context = body.context();
		Vector stepped = collisions.collideBoundingBox(new Vector(movement.x, stepHeight, movement.z), box, context);
		Vector rise = collisions.collideBoundingBox(new Vector(0.0, stepHeight, 0.0),
				box.expandTowards(movement.x, 0.0, movement.z), context);
		if (rise.y < stepHeight) {
			Vector across = collisions.collideBoundingBox(new Vector(movement.x, 0.0, movement.z), box.move(rise), context)
					.add(rise);
			if (across.horizontalDistanceSqr() > stepped.horizontalDistanceSqr()) stepped = across;
		}
		if (stepped.horizontalDistanceSqr() <= collided.horizontalDistanceSqr()) return collided;

		return stepped.add(collisions.collideBoundingBox(new Vector(0.0, -stepped.y + movement.y, 0.0),
				box.move(stepped), context));
	}
}
