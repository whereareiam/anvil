package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Axis;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Box;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.java.physics.server.Effects;
import me.whereareiam.anvil.capability.movement.java.physics.server.MobEffect;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerAttribute;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.move.EntityMove;
import me.whereareiam.anvil.capability.movement.java.physics.world.BlockReader;
import me.whereareiam.anvil.capability.movement.java.physics.world.Collisions;
import me.whereareiam.anvil.capability.movement.java.physics.world.Fluids;
import org.jetbrains.annotations.NotNull;

/**
 * How the player travels with its input, as {@code LivingEntity.travel} and {@code Player.travel} move it: through the
 * air with the friction of the block below, gravity, levitation and slow falling ({@code travelInAir}), through water
 * with its water movement efficiency and dolphin's grace ({@code travelInWater}), through lava ({@code travelInLava}),
 * climbing ({@code handleOnClimbable}) and flying, which keeps {@code 0.6} of the vertical speed the tick started with.
 */
public final class Travel {
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);

	private final @NotNull PlayerBody body;
	private final @NotNull BlockReader reader;
	private final @NotNull EntityMove move;
	private final @NotNull FluidState fluids;
	private final @NotNull BlockEffects effects;
	private final @NotNull Climbing climbing;
	private float legacyFlyingSpeed = 0.02F;

	/**
	 * Creates the travel of a player.
	 *
	 * @param body the player
	 * @param reader the blocks its client knows
	 * @param move its movement against the blocks
	 * @param fluids how it is in fluids
	 * @param effects what the blocks do to it
	 */
	public Travel(
			@NotNull PlayerBody body,
			@NotNull BlockReader reader,
			@NotNull EntityMove move,
			@NotNull FluidState fluids,
			@NotNull BlockEffects effects
	) {
		this.body = body;
		this.reader = reader;
		this.move = move;
		this.fluids = fluids;
		this.effects = effects;
		this.climbing = new Climbing(body, reader);
	}

	/**
	 * Travels one tick with the player's input ({@code Player.travel}).
	 */
	public void travel() {
		Vector input = new Vector(body.getXxa(), 0.0, body.getZza());
		if (!body.getServer().isFlying()) {
			travelBody(input);
			return;
		}

		double vertical = body.getDeltaMovement().y;
		travelBody(input);
		body.setDeltaMovement(body.getDeltaMovement().with(Axis.Y, vertical * 0.6));
	}

	/**
	 * Keeps the air control speed for the next tick, as {@code Player.aiStep} sets it before 1.19.4.
	 */
	public void afterAiStep() {
		legacyFlyingSpeed = body.isSprinting() ? 0.02F + 0.006F : 0.02F;
	}

	private void travelBody(@NotNull Vector input) {
		boolean affected = !body.getServer().isFlying();
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
		double earlyGravity = effectiveGravity();
		float friction = body.isOnGround() ? effects.friction() : 1.0F;
		float drag = friction * 0.91F;
		moveRelative(frictionInfluencedSpeed(friction), input);
		body.setDeltaMovement(climbing.handleOnClimbable(body.getDeltaMovement()));
		move.move(body.getDeltaMovement());
		Vector movement = body.getDeltaMovement();
		if ((body.isHorizontalCollision() || body.isJumping()) && climbing.onClimbable())
			movement = new Vector(movement.x, 0.2, movement.z);

		double gravity = body.has(ClientBehaviour.GRAVITY_AFTER_MOVE) ? effectiveGravity() : earlyGravity;
		double vertical = vertical(movement.y, gravity);
		body.setDeltaMovement(new Vector(movement.x * drag, vertical * 0.98F, movement.z * drag));
	}

	private double vertical(double vertical, double gravity) {
		Effects active = body.getServer().getEffects();
		if (active.has(MobEffect.LEVITATION))
			return vertical + (0.05 * (active.amplifier(MobEffect.LEVITATION).orElseThrow() + 1) - vertical) * 0.2;
		if (effects.hasChunkBelow()) return vertical - gravity;

		return body.getPosition().y > reader.minY() ? -0.1 : 0.0;
	}

	/**
	 * {@code getFrictionInfluencedSpeed}: on the ground the player's speed scaled by the friction, which
	 * {@code Player.getSpeed} reads from the movement speed attribute at that moment; in the air the air control speed
	 * ({@code Player.getFlyingSpeed}).
	 */
	private float frictionInfluencedSpeed(float friction) {
		if (body.isOnGround()) return speed() * (0.21600002F / (friction * friction * friction));
		if (body.getServer().isFlying())
			return body.isSprinting() ? body.getServer().getFlyingSpeed() * 2.0F : body.getServer().getFlyingSpeed();
		if (!body.has(ClientBehaviour.LIVE_FLYING_SPEED)) return legacyFlyingSpeed;

		return body.isSprinting() ? 0.025999999F : 0.02F;
	}

	private void travelInWater(@NotNull Vector input) {
		double gravity = effectiveGravity();
		boolean falling = body.getDeltaMovement().y <= 0.0;
		double startY = body.getPosition().y;
		float slowDown = body.isSprinting() ? 0.9F : 0.8F;
		float speed = 0.02F;
		float efficiency = (float) body.getServer().getAttributes().value(PlayerAttribute.WATER_MOVEMENT_EFFICIENCY);
		if (!body.isOnGround()) efficiency *= 0.5F;
		if (efficiency > 0.0F) {
			slowDown += (0.54600006F - slowDown) * efficiency;
			speed += (speed() - speed) * efficiency;
		}
		if (body.getServer().getEffects().has(MobEffect.DOLPHINS_GRACE)) slowDown = 0.96F;

		moveRelative(speed, input);
		move.move(body.getDeltaMovement());
		Vector movement = body.getDeltaMovement();
		if (body.isHorizontalCollision() && climbing.onClimbable()) movement = new Vector(movement.x, 0.2, movement.z);
		movement = movement.multiply(slowDown, 0.8F, slowDown);
		body.setDeltaMovement(fluidFallingAdjusted(gravity, falling, movement));
		jumpOutOfFluid(startY);
	}

	private void travelInLava(@NotNull Vector input) {
		double gravity = effectiveGravity();
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
		if (new Collisions(reader).noCollision(moved, body.context()) && !new Fluids(reader).containsAny(moved))
			body.setDeltaMovement(new Vector(delta.x, 0.3F, delta.z));
	}

	/**
	 * {@code Entity.moveRelative}: accelerates the player along its input, turned by its yaw.
	 */
	private void moveRelative(float amount, @NotNull Vector input) {
		double lengthSqr = input.lengthSqr();
		if (lengthSqr < 1.0E-7) return;

		Vector scaled = (lengthSqr > 1.0 ? input.normalize() : input).scale(amount);
		Trigonometry trigonometry = body.getRules().trigonometry();
		float sin = trigonometry.sin(body.getYaw() * DEGREES_TO_RADIANS);
		float cos = trigonometry.cos(body.getYaw() * DEGREES_TO_RADIANS);
		body.addDeltaMovement(scaled.x * cos - scaled.z * sin, scaled.y, scaled.z * cos + scaled.x * sin);
	}

	/**
	 * {@code LivingEntity.getEffectiveGravity}: the gravity attribute, at most {@code 0.01} while the player falls with
	 * slow falling.
	 */
	private double effectiveGravity() {
		double gravity = body.getServer().getAttributes().value(PlayerAttribute.GRAVITY);
		boolean slow = body.getDeltaMovement().y <= 0.0 && body.getServer().getEffects().has(MobEffect.SLOW_FALLING);
		return slow ? Math.min(gravity, 0.01) : gravity;
	}

	private float speed() {
		return (float) body.getServer().getAttributes().value(PlayerAttribute.MOVEMENT_SPEED);
	}
}
