package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.java.physics.server.MobEffect;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerAttribute;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import org.jetbrains.annotations.NotNull;

/**
 * The jump key, as the vanilla client reads it: a jump from the ground with the jump strength, the block's jump factor
 * and jump boost, ten ticks apart while the key stays held ({@code LivingEntity.aiStep}, {@code jumpFromGround}),
 * rising in water and lava, a sprint jump's push along the view, and, for a player that may fly, flight toggled by
 * pressing jump twice within seven ticks, forced on for a spectator, steered up and down with jump and sneak, and ended
 * by landing ({@code LocalPlayer.aiStep}). Each time the client starts or stops flying on its own, it tells the server
 * through the abilities packet ({@code onUpdateAbilities}).
 */
public final class Jumping {
	private static final int JUMP_DELAY = 10;
	private static final int DOUBLE_TAP_WINDOW = 7;

	private final @NotNull PlayerBody body;
	private final @NotNull FluidState fluids;
	private final @NotNull BlockEffects effects;
	private int noJumpDelay;
	private int jumpTriggerTime;

	/**
	 * Creates the jumping of a player.
	 *
	 * @param body the player
	 * @param fluids how it is in fluids
	 * @param effects what the blocks do to it
	 */
	public Jumping(@NotNull PlayerBody body, @NotNull FluidState fluids, @NotNull BlockEffects effects) {
		this.body = body;
		this.fluids = fluids;
		this.effects = effects;
	}

	/**
	 * Toggles and steers flight with the keys of this tick, before the player's own movement ({@code LocalPlayer.aiStep}).
	 *
	 * @param wasJumping whether the jump key was held in the previous tick
	 */
	public void flight(boolean wasJumping) {
		PlayerState server = body.getServer();
		if (server.isMayFly()) toggleFlight(server, wasJumping);
		boolean affected = !server.isFlying();
		if (fluids.isTouchingWater() && body.getKeys().isSneak() && affected) body.addDeltaMovement(0.0, -0.04F, 0.0);
		if (!server.isFlying()) return;

		int direction = (body.getKeys().isSneak() ? -1 : 0) + (body.getKeys().isJump() ? 1 : 0);
		if (direction != 0) body.addDeltaMovement(0.0, direction * server.getFlyingSpeed() * 3.0F, 0.0);
	}

	private void toggleFlight(@NotNull PlayerState server, boolean wasJumping) {
		if (server.isSpectator()) {
			if (server.isFlying()) return;

			server.setFlying(true);
			body.updateAbilities();
			return;
		}
		if (wasJumping || !body.getKeys().isJump()) return;
		if (jumpTriggerTime == 0) {
			jumpTriggerTime = DOUBLE_TAP_WINDOW;
			return;
		}

		server.setFlying(!server.isFlying());
		if (server.isFlying() && body.isOnGround()) jumpFromGround();
		body.updateAbilities();
		jumpTriggerTime = 0;
	}

	/**
	 * Counts the jump delay and the double tap down, at the start of the player's own movement.
	 */
	public void countDown() {
		if (jumpTriggerTime > 0) jumpTriggerTime--;
		if (noJumpDelay > 0) noJumpDelay--;
	}

	/**
	 * Jumps or rises in a fluid while the jump key is held and the player does not fly ({@code LivingEntity.aiStep}).
	 */
	public void jump() {
		if (!body.isJumping() || body.getServer().isFlying()) {
			noJumpDelay = 0;
			return;
		}

		double depth = fluids.isInLava() ? fluids.getLavaHeight() : fluids.getWaterHeight();
		boolean inWater = fluids.isTouchingWater() && depth > 0.0;
		double threshold = fluids.fluidJumpThreshold();
		boolean deep = !(body.isOnGround() && !(depth > threshold));
		if (inWater && deep || fluids.isInLava() && deep) {
			body.addDeltaMovement(0.0, 0.04F, 0.0);
			return;
		}
		if ((body.isOnGround() || inWater && depth <= threshold) && noJumpDelay == 0) {
			jumpFromGround();
			noJumpDelay = JUMP_DELAY;
		}
	}

	/**
	 * Lands the flight of a player that is not a spectator and tells the server, after the player's own movement.
	 */
	public void land() {
		if (!body.isOnGround() || !body.getServer().isFlying() || body.getServer().isSpectator()) return;

		body.getServer().setFlying(false);
		body.updateAbilities();
	}

	/**
	 * {@code LivingEntity.jumpFromGround}: rises with the jump power, from 1.21.2 keeping a higher upward speed, and
	 * pushes a sprinting player along its view.
	 */
	private void jumpFromGround() {
		float power = jumpPower();
		if (power <= 1.0E-5F) return;

		Vector delta = body.getDeltaMovement();
		double rise = body.has(ClientBehaviour.PATH_BLOCK_EFFECTS) ? Math.max(power, delta.y) : power;
		body.setDeltaMovement(new Vector(delta.x, rise, delta.z));
		if (!body.isSprinting()) return;

		Trigonometry trigonometry = body.getRules().trigonometry();
		float radians = body.getYaw() * (float) (Math.PI / 180.0);
		if (body.has(ClientBehaviour.DOUBLE_SPRINT_JUMP_BOOST))
			body.addDeltaMovement(-trigonometry.sin(radians) * 0.2, 0.0, trigonometry.cos(radians) * 0.2);
		else body.addDeltaMovement(-trigonometry.sin(radians) * 0.2F, 0.0, trigonometry.cos(radians) * 0.2F);
	}

	/**
	 * {@code LivingEntity.getJumpPower}: the jump strength times the block's jump factor, plus {@code 0.1F} per level of
	 * jump boost ({@code getJumpBoostPower}).
	 */
	private float jumpPower() {
		float strength = (float) body.getServer().getAttributes().value(PlayerAttribute.JUMP_STRENGTH);
		int boost = body.getServer().getEffects().amplifier(MobEffect.JUMP_BOOST).orElse(-1);
		float boostPower = boost < 0 ? 0.0F : 0.1F * (boost + 1.0F);
		return strength * 1.0F * effects.jumpFactor() + boostPower;
	}
}
