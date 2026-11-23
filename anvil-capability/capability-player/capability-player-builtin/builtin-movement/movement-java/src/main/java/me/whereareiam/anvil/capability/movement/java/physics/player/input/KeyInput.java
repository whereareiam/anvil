package me.whereareiam.anvil.capability.movement.java.physics.player.input;

import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerBody;
import me.whereareiam.anvil.capability.movement.java.physics.player.Pose;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerAttributes;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.edition.java.physics.entity.FluidState;
import me.whereareiam.anvil.edition.java.physics.living.effect.MobEffect;
import org.jetbrains.annotations.NotNull;

/**
 * How the vanilla client turns the keys it holds into its player's input, sprinting and crouching
 * ({@code LocalPlayer.aiStep} and {@code applyInput}), in one of its two forms: a normalized direction from 1.21.5
 * ({@link VectorInput}), impulses before ({@link LegacyInput}).
 */
public sealed interface KeyInput permits VectorInput, LegacyInput {
	/**
	 * The ticks within which pressing forward again starts a sprint, the default of the client's sprint window option.
	 */
	int SPRINT_WINDOW = 7;

	/**
	 * Creates the key input of a player's client version.
	 *
	 * @param body the player
	 * @param pose its pose
	 * @param fluids how it is in fluids
	 * @return the key input
	 */
	static @NotNull KeyInput of(@NotNull PlayerBody body, @NotNull Pose pose, @NotNull FluidState fluids) {
		if (body.has(ClientBehaviour.VECTOR_INPUT)) return new VectorInput(body, pose, fluids);

		return new LegacyInput(body, pose, fluids);
	}

	/**
	 * Reads the keys held this tick, and starts or stops crouching and sprinting with them.
	 *
	 * @param keys the held keys
	 */
	void read(@NotNull MovementInput keys);

	/**
	 * Turns the keys into the player's input and jumping ({@code LocalPlayer.serverAiStep}, {@code applyInput}).
	 */
	void apply();

	/**
	 * Tells whether the client crouches for its input where the player is ({@code LocalPlayer.aiStep}): it does not fly,
	 * fits crouching, and holds sneak or cannot stand up.
	 *
	 * @param body the player
	 * @param pose its pose
	 * @param wasSneaking whether sneak was held in the previous tick
	 * @return whether it crouches
	 */
	static boolean crouches(@NotNull PlayerBody body, @NotNull Pose pose, boolean wasSneaking) {
		return !body.server().flying() && pose.canCrouch() && (wasSneaking || !pose.canStand());
	}

	/**
	 * Tells whether the player moves slowly ({@code isMovingSlowly}): it crouches or crawls out of water.
	 *
	 * @param body the player
	 * @param pose its pose
	 * @param fluids how it is in fluids
	 * @return whether its input is scaled by the sneaking speed
	 */
	static boolean movesSlowly(@NotNull PlayerBody body, @NotNull Pose pose, @NotNull FluidState fluids) {
		return body.crouching() || pose.isSwimmingPose() && !fluids.touchingWater();
	}

	/**
	 * Tells whether the player may sprint ({@code isSprintingPossible}): it is not blind, has the food, and does not
	 * wade unless shallow water is allowed.
	 *
	 * @param body the player
	 * @param fluids how it is in fluids
	 * @param allowShallowWater whether wading allows it, as flying does
	 * @return whether it may sprint
	 */
	static boolean sprintingPossible(@NotNull PlayerBody body, @NotNull FluidState fluids, boolean allowShallowWater) {
		return !body.effects().has(MobEffect.BLINDNESS) && body.server().hasEnoughFood()
				&& (allowShallowWater || !inShallowWater(body, fluids));
	}

	/**
	 * Tells whether the player wades ({@code LocalPlayer.isInShallowWater}): it touches water, but its eyes were not
	 * under water when the tick started.
	 *
	 * @param body the player
	 * @param fluids how it is in fluids
	 * @return whether it wades
	 */
	static boolean inShallowWater(@NotNull PlayerBody body, @NotNull FluidState fluids) {
		return fluids.touchingWater() && !body.underWater();
	}

	/**
	 * The player's sneaking speed attribute.
	 *
	 * @param body the player
	 * @return the factor of the input while it moves slowly
	 */
	static float sneakingSpeed(@NotNull PlayerBody body) {
		return (float) body.attributes().value(PlayerAttributes.SNEAKING_SPEED);
	}

	/**
	 * {@code calculateImpulse}: one key of a pair gives one, the other minus one, both or neither zero.
	 *
	 * @param positive whether the positive key is held
	 * @param negative whether the negative key is held
	 * @return the impulse
	 */
	static float impulse(boolean positive, boolean negative) {
		if (positive == negative) return 0.0F;

		return positive ? 1.0F : -1.0F;
	}
}
