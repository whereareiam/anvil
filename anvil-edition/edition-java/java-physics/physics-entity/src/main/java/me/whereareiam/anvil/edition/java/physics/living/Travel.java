package me.whereareiam.anvil.edition.java.physics.living;

import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import me.whereareiam.anvil.edition.java.physics.entity.BlockEffects;
import me.whereareiam.anvil.edition.java.physics.entity.EntityMove;
import me.whereareiam.anvil.edition.java.physics.entity.FluidState;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.living.attribute.LivingAttributes;
import me.whereareiam.anvil.edition.java.physics.living.effect.Effects;
import me.whereareiam.anvil.edition.java.physics.living.effect.MobEffect;
import me.whereareiam.anvil.edition.java.physics.world.Collisions;
import me.whereareiam.anvil.edition.java.physics.world.Fluids;
import org.jetbrains.annotations.NotNull;

/**
 * How a living entity travels with its input, as {@code LivingEntity.travel} moves it: through the air with the
 * friction of the block below, gravity, levitation and slow falling ({@code travelInAir}), through water with its water
 * movement efficiency and dolphin's grace ({@code travelInWater}), through lava ({@code travelInLava}) and climbing
 * ({@code handleOnClimbable}). Fluids slow only an entity they affect ({@link LivingBody#isAffectedByFluids}).
 */
public final class Travel {
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);

	private final @NotNull LivingBody body;
	private final @NotNull EntityMove move;
	private final @NotNull FluidState fluids;
	private final @NotNull BlockEffects effects;
	private final @NotNull Climbing climbing;
	private final @NotNull Collisions collisions;

	/**
	 * Creates the travel of a living entity.
	 *
	 * @param body the entity
	 * @param fluids how it is in fluids
	 * @param effects what the blocks do to it
	 */
	public Travel(@NotNull LivingBody body, @NotNull FluidState fluids, @NotNull BlockEffects effects) {
		this.body = body;
		this.move = new EntityMove(body, effects, fluids, body);
		this.fluids = fluids;
		this.effects = effects;
		this.climbing = new Climbing(body);
		this.collisions = new Collisions(body.getLevel());
	}

	/**
	 * Travels one tick with an input ({@code LivingEntity.travel}).
	 *
	 * @param input the strafing, vertical and forward input
	 */
	public void travel(@NotNull Vector input) {
		boolean affected = body.isAffectedByFluids();
		if (affected && fluids.isTouchingWater()) {
			travelInWater(input);
			return;
		}
		if (affected && fluids.isInLava()) {
			travelInLava(input);
			return;
		}

		travelInAir(input);
	}

	private void travelInAir(@NotNull Vector input) {
		double earlyGravity = body.effectiveGravity();
		float friction = body.isOnGround() ? groundFriction() : 1.0F;
		float drag = friction * airDrag(0.91F);
		moveRelative(frictionInfluencedSpeed(friction), input);
		body.setDeltaMovement(climbing.handleOnClimbable(body.getDeltaMovement()));
		move.move(body.getDeltaMovement());
		Vector movement = body.getDeltaMovement();
		if ((body.isHorizontalCollision() || body.isJumping()) && body.onClimbable())
			movement = new Vector(movement.x, 0.2, movement.z);

		double gravity = body.has(EntityBehaviour.GRAVITY_AFTER_MOVE) ? body.effectiveGravity() : earlyGravity;
		double vertical = vertical(movement.y, gravity);
		body.setDeltaMovement(new Vector(movement.x * drag, vertical * airDrag(0.98F), movement.z * drag));
	}

	/**
	 * The friction of the block below, from 26.2 modified by the friction modifier attribute.
	 */
	private float groundFriction() {
		float friction = effects.friction();
		if (!body.has(EntityBehaviour.FRICTION_MODIFIERS)) return friction;

		return body.modifiedFriction(friction, LivingAttributes.FRICTION_MODIFIER);
	}

	/**
	 * A drag of the air, from 26.2 modified by the air drag modifier attribute.
	 */
	private float airDrag(float drag) {
		if (!body.has(EntityBehaviour.FRICTION_MODIFIERS)) return drag;

		return body.modifiedFriction(drag, LivingAttributes.AIR_DRAG_MODIFIER);
	}

	private double vertical(double vertical, double gravity) {
		Effects active = body.getEffects();
		if (active.has(MobEffect.LEVITATION))
			return vertical + (0.05 * (active.amplifier(MobEffect.LEVITATION).orElseThrow() + 1) - vertical) * 0.2;

		return vertical - gravity;
	}

	/**
	 * {@code getFrictionInfluencedSpeed}: on the ground the entity's speed scaled by the friction, from 26.2 only where
	 * the friction is above {@code 0.6}, in the air the speed of its control there ({@link LivingBody#flyingSpeed}).
	 */
	private float frictionInfluencedSpeed(float friction) {
		if (!body.isOnGround()) return body.flyingSpeed();
		if (body.has(EntityBehaviour.FRICTION_MODIFIERS) && !(friction > 0.6)) return body.getSpeed();

		return body.getSpeed() * (0.21600002F / (friction * friction * friction));
	}

	private void travelInWater(@NotNull Vector input) {
		double gravity = body.effectiveGravity();
		boolean falling = body.getDeltaMovement().y <= 0.0;
		double startY = body.getPosition().y;
		float slowDown = body.isSprinting() ? 0.9F : 0.8F;
		float speed = 0.02F;
		float efficiency = (float) body.getAttributes().value(LivingAttributes.WATER_MOVEMENT_EFFICIENCY);
		if (!body.isOnGround()) efficiency *= 0.5F;
		if (efficiency > 0.0F) {
			slowDown += (0.54600006F - slowDown) * efficiency;
			speed += (body.getSpeed() - speed) * efficiency;
		}
		if (body.getEffects().has(MobEffect.DOLPHINS_GRACE)) slowDown = 0.96F;

		moveRelative(speed, input);
		move.move(body.getDeltaMovement());
		Vector movement = body.getDeltaMovement();
		if (body.isHorizontalCollision() && body.onClimbable()) movement = new Vector(movement.x, 0.2, movement.z);
		movement = movement.multiply(slowDown, 0.8F, slowDown);
		body.setDeltaMovement(fluidFallingAdjusted(gravity, falling, movement));
		jumpOutOfFluid(startY);
	}

	private void travelInLava(@NotNull Vector input) {
		double gravity = body.effectiveGravity();
		boolean falling = body.getDeltaMovement().y <= 0.0;
		double startY = body.getPosition().y;
		moveRelative(0.02F, input);
		move.move(body.getDeltaMovement());
		Vector delta = body.getDeltaMovement();
		body.setDeltaMovement(fluids.getLavaHeight() <= fluids.fluidJumpThreshold()
				? fluidFallingAdjusted(gravity, falling, delta.multiply(0.5, 0.8F, 0.5))
				: delta.scale(0.5));
		if (gravity != 0.0) body.addDeltaMovement(0.0, -gravity / 4.0, 0.0);
		jumpOutOfFluid(startY);
	}

	private @NotNull Vector fluidFallingAdjusted(double gravity, boolean falling, @NotNull Vector movement) {
		if (gravity == 0.0 || body.isSprinting()) return movement;

		boolean settled = falling && Math.abs(movement.y - 0.005) >= 0.003 && Math.abs(movement.y - gravity / 16.0) < 0.003;
		return new Vector(movement.x, settled ? -0.003 : movement.y - gravity / 16.0, movement.z);
	}

	private void jumpOutOfFluid(double startY) {
		Vector delta = body.getDeltaMovement();
		if (!body.isHorizontalCollision()) return;

		Box moved = body.getBox().move(delta.x, delta.y + 0.6F - body.getPosition().y + startY, delta.z);
		Fluids fluidsAround = new Fluids(body.getLevel(), body.getRules().vectorLength());
		if (collisions.noCollision(moved, body.context()) && !fluidsAround.containsAny(moved))
			body.setDeltaMovement(new Vector(delta.x, 0.3F, delta.z));
	}

	/**
	 * {@code Entity.moveRelative}: accelerates the entity along its input, turned by its yaw; an input too short to have a
	 * direction adds zero ({@code getInputVector}), which still turns a negative zero speed positive.
	 */
	private void moveRelative(float amount, @NotNull Vector input) {
		double lengthSqr = input.lengthSqr();
		if (lengthSqr < 1.0E-7) {
			body.addDeltaMovement(0.0, 0.0, 0.0);
			return;
		}

		Vector scaled = (lengthSqr > 1.0 ? body.getRules().vectorLength().normalize(input) : input).scale(amount);
		Trigonometry trigonometry = body.getRules().trigonometry();
		float sin = trigonometry.sin(body.getYaw() * DEGREES_TO_RADIANS);
		float cos = trigonometry.cos(body.getYaw() * DEGREES_TO_RADIANS);
		body.addDeltaMovement(scaled.x * cos - scaled.z * sin, scaled.y, scaled.z * cos + scaled.x * sin);
	}
}
