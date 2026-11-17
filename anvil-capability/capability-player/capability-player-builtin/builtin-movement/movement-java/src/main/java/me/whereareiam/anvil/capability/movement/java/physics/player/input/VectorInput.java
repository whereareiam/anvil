package me.whereareiam.anvil.capability.movement.java.physics.player.input;

import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerBody;
import me.whereareiam.anvil.capability.movement.java.physics.player.Pose;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.physics.entity.FluidState;
import org.jetbrains.annotations.NotNull;

/**
 * The key input of the clients from 1.21.5, whose keys become a normalized direction ({@code KeyboardInput.tick},
 * {@code Vec2.normalized}) that {@code LocalPlayer.modifyInput} scales by {@code 0.98F} and the sneaking speed and
 * corrects for diagonal movement; sprinting starts on a second press of forward within the sprint window or the sprint
 * key ({@code canStartSprinting}) and stops without forward input, at a wall the player does not run along, or when it
 * is no longer possible ({@code shouldStopRunSprinting}).
 */
public final class VectorInput implements KeyInput {
	private final @NotNull PlayerBody body;
	private final @NotNull Pose pose;
	private final @NotNull FluidState fluids;
	private float leftImpulse;
	private float forwardImpulse;
	private int sprintTriggerTime;

	VectorInput(@NotNull PlayerBody body, @NotNull Pose pose, @NotNull FluidState fluids) {
		this.body = body;
		this.pose = pose;
		this.fluids = fluids;
	}

	@Override
	public void read(@NotNull MovementInput keys) {
		if (sprintTriggerTime > 0) sprintTriggerTime--;

		boolean wasSneaking = body.getKeys().isSneak();
		boolean hadForwardImpulse = hasForwardImpulse();
		body.setCrouching(KeyInput.crouches(body, pose, wasSneaking));
		body.setKeys(keys);
		float left = KeyInput.impulse(keys.isLeft(), keys.isRight());
		float forward = KeyInput.impulse(keys.isForward(), keys.isBackward());
		float length = (float) Math.sqrt(left * left + forward * forward);
		leftImpulse = length < 1.0E-4F ? 0.0F : left / length;
		forwardImpulse = length < 1.0E-4F ? 0.0F : forward / length;
		if (wasSneaking || keys.isBackward()) sprintTriggerTime = 0;

		if (canStartSprinting()) startSprinting(hadForwardImpulse, keys.isSprint());
		if (body.isSprinting() && shouldStopSprinting()) body.setSprinting(false);
	}

	private void startSprinting(boolean hadForwardImpulse, boolean sprintKey) {
		if (!hadForwardImpulse) pressForward();
		if (sprintKey) body.setSprinting(true);
	}

	/**
	 * A fresh press of forward sprints when it is the second within the sprint window, else opens the window.
	 */
	private void pressForward() {
		if (sprintTriggerTime > 0) {
			body.setSprinting(true);
			return;
		}

		sprintTriggerTime = SPRINT_WINDOW;
	}

	private boolean canStartSprinting() {
		return !body.isSprinting()
				&& hasForwardImpulse()
				&& KeyInput.sprintingPossible(body, fluids, body.getServer().isFlying())
				&& (!KeyInput.movesSlowly(body, pose, fluids) || body.isUnderWater());
	}

	private boolean shouldStopSprinting() {
		return !KeyInput.sprintingPossible(body, fluids, body.getServer().isFlying())
				|| !hasForwardImpulse()
				|| body.isHorizontalCollision() && !body.isMinorHorizontalCollision();
	}

	private boolean hasForwardImpulse() {
		return forwardImpulse > 1.0E-5F;
	}

	/**
	 * {@code LocalPlayer.applyInput} with {@code modifyInput}.
	 */
	@Override
	public void apply() {
		body.setJumping(body.getKeys().isJump());
		float x = leftImpulse;
		float y = forwardImpulse;
		if (x * x + y * y == 0.0F) {
			body.setXxa(x);
			body.setZza(y);
			return;
		}

		x *= 0.98F;
		y *= 0.98F;
		if (KeyInput.movesSlowly(body, pose, fluids)) {
			float sneaking = KeyInput.sneakingSpeed(body);
			x *= sneaking;
			y *= sneaking;
		}
		squareMovement(x, y);
	}

	/**
	 * {@code modifyInputSpeedForSquareMovement}: a diagonal input reaches the corner of the unit square, not of the
	 * circle.
	 */
	private void squareMovement(float x, float y) {
		float length = (float) Math.sqrt(x * x + y * y);
		if (length <= 0.0F) {
			body.setXxa(x);
			body.setZza(y);
			return;
		}

		float unitX = x * (1.0F / length);
		float unitY = y * (1.0F / length);
		float absX = Math.abs(unitX);
		float absY = Math.abs(unitY);
		float ratio = absY > absX ? absX / absY : absY / absX;
		float scale = Math.min(length * (float) Math.sqrt(1.0F + ratio * ratio), 1.0F);
		body.setXxa(unitX * scale);
		body.setZza(unitY * scale);
	}
}
