package me.whereareiam.anvil.capability.movement.java.physics.player.input;

import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerBody;
import me.whereareiam.anvil.capability.movement.java.physics.player.Pose;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.edition.java.physics.entity.FluidState;
import org.jetbrains.annotations.NotNull;

/**
 * The key input of the clients before 1.21.5, whose keys become impulses of one each ({@code KeyboardInput.tick}),
 * scaled by the sneaking speed while the player moves slowly, which the living entity's step then damps by
 * {@code 0.98F}; sprinting starts on a second press of forward within seven ticks on the ground or under water, or with
 * the sprint key, given enough forward impulse, food and sight, and stops without forward input or food, at a wall the
 * player does not run along, or when it wades.
 */
public final class LegacyInput implements KeyInput {
	private final @NotNull PlayerBody body;
	private final @NotNull Pose pose;
	private final @NotNull FluidState fluids;
	private float leftImpulse;
	private float forwardImpulse;
	private int sprintTriggerTime;

	LegacyInput(@NotNull PlayerBody body, @NotNull Pose pose, @NotNull FluidState fluids) {
		this.body = body;
		this.pose = pose;
		this.fluids = fluids;
	}

	@Override
	public void read(@NotNull MovementInput keys) {
		if (sprintTriggerTime > 0) sprintTriggerTime--;

		boolean wasSneaking = body.getKeys().isSneak();
		boolean hadEnoughImpulse = enoughImpulseToStartSprinting();
		body.setCrouching(KeyInput.crouches(body, pose, wasSneaking));
		body.setKeys(keys);
		forwardImpulse = KeyInput.impulse(keys.isForward(), keys.isBackward());
		leftImpulse = KeyInput.impulse(keys.isLeft(), keys.isRight());
		if (KeyInput.movesSlowly(body, pose, fluids)) {
			float sneaking = KeyInput.sneakingSpeed(body);
			leftImpulse *= sneaking;
			forwardImpulse *= sneaking;
		}
		if (wasSneaking) sprintTriggerTime = 0;

		boolean canStart = canStartSprinting();
		boolean doubleTap = (body.isOnGround() || body.isUnderWater()) && !wasSneaking && !hadEnoughImpulse;
		if (doubleTap && canStart) pressForward(keys.isSprint());
		if ((!fluids.isTouchingWater() || body.isUnderWater()) && canStart && keys.isSprint()) body.setSprinting(true);
		if (body.isSprinting() && shouldStopSprinting()) body.setSprinting(false);
	}

	/**
	 * A fresh press of forward opens the sprint window, unless the window is open or the sprint key is held, which
	 * sprints.
	 */
	private void pressForward(boolean sprintKey) {
		if (sprintTriggerTime <= 0 && !sprintKey) {
			sprintTriggerTime = SPRINT_WINDOW;
			return;
		}

		body.setSprinting(true);
	}

	private boolean canStartSprinting() {
		return !body.isSprinting() && enoughImpulseToStartSprinting() && KeyInput.sprintingPossible(body, fluids, true);
	}

	private boolean shouldStopSprinting() {
		boolean exhausted = !(forwardImpulse > 1.0E-5F) || !body.getServer().hasEnoughFood();
		return exhausted
				|| body.isHorizontalCollision() && !body.isMinorHorizontalCollision()
				|| KeyInput.inShallowWater(body, fluids);
	}

	/**
	 * {@code hasEnoughImpulseToStartSprinting}: any forward impulse under water, else at least {@code 0.8}.
	 */
	private boolean enoughImpulseToStartSprinting() {
		return body.isUnderWater() ? forwardImpulse > 1.0E-5F : (double) forwardImpulse >= 0.8;
	}

	/**
	 * {@code LocalPlayer.serverAiStep}: the impulses become the player's input.
	 */
	@Override
	public void apply() {
		body.setXxa(leftImpulse);
		body.setZza(forwardImpulse);
		body.setJumping(body.getKeys().isJump());
	}
}
