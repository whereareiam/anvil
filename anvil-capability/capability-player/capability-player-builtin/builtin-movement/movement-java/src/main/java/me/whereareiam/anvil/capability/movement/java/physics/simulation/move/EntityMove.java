package me.whereareiam.anvil.capability.movement.java.physics.simulation.move;

import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Box;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerAttribute;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.BlockEffects;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.FluidState;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.PlayerBody;
import me.whereareiam.anvil.capability.movement.java.physics.world.BlockReader;
import me.whereareiam.anvil.capability.movement.java.physics.world.Collisions;
import me.whereareiam.anvil.capability.movement.java.physics.world.PlacedShape;
import me.whereareiam.anvil.capability.movement.java.physics.world.EntityContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code Entity.move} for the player's own movement: a block it is stuck in scales the movement once, a sneaking player
 * backs off edges ({@link EdgeBackOff}), the movement collides with the blocks and steps up onto what stops it
 * horizontally ({@code Entity.collide}), and then the collision flags, the block stood on, the fall distance, landing,
 * the blocks inside the player before 1.21.2, and the speed factor of the block below follow. A spectator moves
 * through blocks ({@code noPhysics}).
 */
public final class EntityMove {
	private final @NotNull PlayerBody body;
	private final @NotNull Collisions collisions;
	private final @NotNull EdgeBackOff edge;
	private final @NotNull BlockEffects effects;
	private final @NotNull FluidState fluids;

	/**
	 * Creates the movement of a player.
	 *
	 * @param body the player
	 * @param reader the blocks its client knows
	 * @param effects what the blocks do to it
	 * @param fluids how it is in fluids
	 */
	public EntityMove(
			@NotNull PlayerBody body,
			@NotNull BlockReader reader,
			@NotNull BlockEffects effects,
			@NotNull FluidState fluids
	) {
		this.body = body;
		this.collisions = new Collisions(reader);
		this.edge = new EdgeBackOff(body, collisions);
		this.effects = effects;
		this.fluids = fluids;
	}

	/**
	 * Moves the player by a movement.
	 *
	 * @param requested the movement, usually the player's velocity
	 */
	public void move(@NotNull Vector requested) {
		if (body.getServer().isSpectator()) {
			body.setPosition(body.getPosition().add(requested));
			body.setHorizontalCollision(false);
			body.setMinorHorizontalCollision(false);
			return;
		}

		Vector movement = unstuck(requested);
		movement = edge.backOff(movement, stepHeight());
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
		boolean tiny = body.has(ClientBehaviour.PATH_BLOCK_EFFECTS) && movement.lengthSqr() - lengthSqr < 1.0E-7;
		if (!(lengthSqr > 1.0E-7) && !tiny) return;

		if (body.has(ClientBehaviour.BOX_DRIVEN_POSITION)) body.moveBox(collided);
		else body.setPosition(body.getPosition().add(collided));
	}

	private void land(@NotNull Vector movement, @NotNull Vector collided) {
		boolean collidedX = !equal(movement.x, collided.x);
		boolean collidedZ = !equal(movement.z, collided.z);
		boolean vertical = movement.y != collided.y;
		body.setHorizontalCollision(collidedX || collidedZ);
		body.setOnGround(vertical && movement.y < 0.0);
		if (body.has(ClientBehaviour.SUPPORTING_BLOCK)) effects.checkSupportingBlock(collided);
		body.setMinorHorizontalCollision(body.isHorizontalCollision() && body.has(ClientBehaviour.MINOR_HORIZONTAL_COLLISION)
				&& horizontalCollisionMinor(collided));

		int[] landed = effects.onPos(0.2F);
		checkFallDamage(collided.y);
		Vector delta = body.getDeltaMovement();
		if (body.isHorizontalCollision())
			body.setDeltaMovement(new Vector(collidedX ? 0.0 : delta.x, delta.y, collidedZ ? 0.0 : delta.z));
		if (vertical) effects.fallOn(landed);
		if (body.has(ClientBehaviour.PATH_BLOCK_EFFECTS)) return;

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

		if (body.has(ClientBehaviour.VECTOR_INPUT)) {
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
	 * {@code LocalPlayer.isHorizontalCollisionMinor}: the player runs almost along the wall it touches.
	 */
	private boolean horizontalCollisionMinor(@NotNull Vector movement) {
		Trigonometry trigonometry = body.getRules().trigonometry();
		float radians = body.getYaw() * (float) (Math.PI / 180.0);
		double sin = trigonometry.sin(radians);
		double cos = trigonometry.cos(radians);
		double inputX = body.getXxa() * cos - body.getZza() * sin;
		double inputZ = body.getZza() * cos + body.getXxa() * sin;
		double inputSqr = inputX * inputX + inputZ * inputZ;
		double movementSqr = movement.x * movement.x + movement.z * movement.z;
		if (inputSqr < 1.0E-5F || movementSqr < 1.0E-5F) return false;

		double dot = inputX * movement.x + inputZ * movement.z;
		return Math.acos(dot / Math.sqrt(inputSqr * movementSqr)) < 0.13962634F;
	}

	/**
	 * {@code Entity.collide}: moves the player against the blocks and steps up onto what stops it horizontally.
	 */
	private @NotNull Vector collide(@NotNull Vector movement) {
		Box box = body.getBox();
		EntityContext context = body.context();
		Vector collided = movement.lengthSqr() == 0.0 ? movement : collisions.collideBoundingBox(movement, box, context);
		boolean landing = movement.y != collided.y && movement.y < 0.0;
		boolean blocked = movement.x != collided.x || movement.z != collided.z;
		float stepHeight = stepHeight();
		if (!(stepHeight > 0.0F) || !(landing || body.isOnGround()) || !blocked) return collided;
		if (body.has(ClientBehaviour.CANDIDATE_STEP_HEIGHTS)) return stepUp(movement, collided, landing, stepHeight);

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

	private float stepHeight() {
		return (float) body.getServer().getAttributes().value(PlayerAttribute.STEP_HEIGHT);
	}
}
