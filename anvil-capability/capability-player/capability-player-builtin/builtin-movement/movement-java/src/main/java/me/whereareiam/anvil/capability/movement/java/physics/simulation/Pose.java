package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Box;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerAttribute;
import me.whereareiam.anvil.capability.movement.java.physics.world.Collisions;
import org.jetbrains.annotations.NotNull;

/**
 * The player's pose and the dimensions it gives, as {@code Player.updatePlayerPose} chooses it: the player crouches
 * while it holds sneak and does not fly, stays crouched where it cannot stand up, and crawls in the swimming pose where
 * it cannot even crouch; a spectator takes the pose it wants. Each pose has the player's vanilla dimensions
 * ({@code Player.POSES}) scaled by the scale attribute ({@code EntityDimensions.scale}).
 */
@RequiredArgsConstructor
public final class Pose {
	private final @NotNull PlayerBody body;
	private final @NotNull Collisions collisions;
	private @NotNull Stance stance = Stance.STANDING;

	/**
	 * Gives the player the dimensions of its pose at its scale, as {@code refreshDimensions} does when the pose or the
	 * scale attribute changed.
	 */
	public void refresh() {
		float scale = scale();
		float width = stance.width * scale;
		float height = stance.height * scale;
		float eyes = stance.eyeHeight * scale;
		if (width != body.getWidth() || height != body.getHeight() || eyes != body.getEyeHeight())
			body.dimensions(width, height, eyes);
	}

	/**
	 * Chooses the player's pose at the end of its tick ({@code Player.updatePlayerPose}).
	 */
	public void update() {
		if (!canFit(Stance.SWIMMING)) return;

		Stance wanted = body.getKeys().isSneak() && !body.getServer().isFlying() ? Stance.CROUCHING : Stance.STANDING;
		stance = choose(wanted);
		refresh();
	}

	private @NotNull Stance choose(@NotNull Stance wanted) {
		if (body.getServer().isSpectator() || canFit(wanted)) return wanted;

		return canFit(Stance.CROUCHING) ? Stance.CROUCHING : Stance.SWIMMING;
	}

	/**
	 * Tells whether the player fits where it is while crouching ({@code canPlayerFitWithinBlocksAndEntitiesWhen}).
	 *
	 * @return whether the crouching box is free of blocks
	 */
	public boolean canCrouch() {
		return canFit(Stance.CROUCHING);
	}

	/**
	 * Tells whether the player fits where it is while standing.
	 *
	 * @return whether the standing box is free of blocks
	 */
	public boolean canStand() {
		return canFit(Stance.STANDING);
	}

	/**
	 * Tells whether the player is in the crouching pose.
	 *
	 * @return whether it crouches
	 */
	public boolean isCrouching() {
		return stance == Stance.CROUCHING;
	}

	/**
	 * Tells whether the player is in the swimming pose, which out of water is crawling ({@code isVisuallySwimming}).
	 *
	 * @return whether it is in the swimming pose
	 */
	public boolean isSwimmingPose() {
		return stance == Stance.SWIMMING;
	}

	private boolean canFit(@NotNull Stance wanted) {
		float scale = scale();
		Box box = Box.at(body.getPosition(), wanted.width * scale, wanted.height * scale).deflate(1.0E-7);
		return collisions.noCollision(box, body.context());
	}

	private float scale() {
		return (float) body.getServer().getAttributes().value(PlayerAttribute.SCALE);
	}

	/**
	 * The player's poses that movement reaches, with their dimensions.
	 */
	private enum Stance {
		STANDING(0.6F, 1.8F, 1.62F),
		CROUCHING(0.6F, 1.5F, 1.27F),
		SWIMMING(0.6F, 0.6F, 0.4F);

		private final float width;
		private final float height;
		private final float eyeHeight;

		Stance(float width, float height, float eyeHeight) {
			this.width = width;
			this.height = height;
			this.eyeHeight = eyeHeight;
		}
	}
}
