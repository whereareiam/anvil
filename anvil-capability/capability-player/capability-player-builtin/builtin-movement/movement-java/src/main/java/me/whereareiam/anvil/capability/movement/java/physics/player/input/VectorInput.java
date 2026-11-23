package me.whereareiam.anvil.capability.movement.java.physics.player.input;

import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerBody;
import me.whereareiam.anvil.capability.movement.java.physics.player.Pose;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.edition.java.physics.entity.FluidState;
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

		boolean wasSneaking = body.keys().sneak();
		boolean hadForwardImpulse = hasForwardImpulse();
		body.crouching(KeyInput.crouches(body, pose, wasSneaking));
		body.keys(keys);
		float left = KeyInput.impulse(keys.left(), keys.right());
		float forward = KeyInput.impulse(keys.forward(), keys.backward());
		float length = (float) Math.sqrt(left * left + forward * forward);
		leftImpulse = length < 1.0E-4F ? 0.0F : left / length;
		forwardImpulse = length < 1.0E-4F ? 0.0F : forward / length;
		if (wasSneaking || keys.backward()) sprintTriggerTime = 0;

		if (canStartSprinting()) startSprinting(hadForwardImpulse, keys.sprint());
		if (body.sprinting() && shouldStopSprinting()) body.sprinting(false);
	}

	private void startSprinting(boolean hadForwardImpulse, boolean sprintKey) {
		if (!hadForwardImpulse) pressForward();
		if (sprintKey) body.sprinting(true);
	}

	/**
	 * A fresh press of forward sprints when it is the second within the sprint window, else opens the window.
	 */
	private void pressForward() {
		if (sprintTriggerTime > 0) {
			body.sprinting(true);
			return;
		}

		sprintTriggerTime = SPRINT_WINDOW;
	}

	private boolean canStartSprinting() {
		return !body.sprinting()
				&& hasForwardImpulse()
				&& KeyInput.sprintingPossible(body, fluids, body.server().flying())
				&& (!KeyInput.movesSlowly(body, pose, fluids) || body.underWater());
	}

	private boolean shouldStopSprinting() {
		return !KeyInput.sprintingPossible(body, fluids, body.server().flying())
				|| !hasForwardImpulse()
				|| body.horizontalCollision() && !body.minorHorizontalCollision();
	}

	private boolean hasForwardImpulse() {
		return forwardImpulse > 1.0E-5F;
	}

	/**
	 * {@code LocalPlayer.applyInput} with {@code modifyInput}.
	 */
	@Override
	public void apply() {
		body.jumping(body.keys().jump());
		float x = leftImpulse;
		float y = forwardImpulse;
		if (x * x + y * y == 0.0F) {
			body.xxa(x);
			body.zza(y);
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
			body.xxa(x);
			body.zza(y);
			return;
		}

		float unitX = x * (1.0F / length);
		float unitY = y * (1.0F / length);
		float absX = Math.abs(unitX);
		float absY = Math.abs(unitY);
		float ratio = absY > absX ? absX / absY : absY / absX;
		float scale = Math.min(length * (float) Math.sqrt(1.0F + ratio * ratio), 1.0F);
		body.xxa(unitX * scale);
		body.zza(unitY * scale);
	}
}
