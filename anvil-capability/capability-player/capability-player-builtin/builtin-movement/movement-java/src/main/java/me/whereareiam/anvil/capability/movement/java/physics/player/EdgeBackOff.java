package me.whereareiam.anvil.capability.movement.java.physics.player;

import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.Collisions;
import org.jetbrains.annotations.NotNull;

/**
 * {@code Player.maybeBackOffFromEdge}: a player that holds sneak and does not fly, on the ground or about to land, does
 * not walk off an edge it would fall from by more than its step height; each horizontal component shrinks by
 * {@code 0.05} until the box no longer could fall there. From 1.20.5 the test is whether the box could fall the step
 * height ({@code canFallAtLeast}), skipped while the player moves up; before, whether the box moved down the step
 * height touches no block.
 */
final class EdgeBackOff {
	private static final double STEP = 0.05;

	private final @NotNull PlayerBody body;
	private final @NotNull Collisions collisions;

	/**
	 * Creates the edge back-off of a player.
	 *
	 * @param body the player
	 */
	EdgeBackOff(@NotNull PlayerBody body) {
		this.body = body;
		this.collisions = new Collisions(body.getLevel());
	}

	/**
	 * Shortens a movement that would take the sneaking player off an edge.
	 *
	 * @param movement the movement
	 * @param stepHeight the player's step height ({@code maxUpStep})
	 * @return the movement that keeps it on the edge
	 */
	@NotNull Vector backOff(@NotNull Vector movement, float stepHeight) {
		if (!body.isShiftKeyDown() || body.getServer().isFlying()) return movement;
		if (!body.has(ClientBehaviour.FALL_TESTING_EDGE_BACK_OFF)) return legacy(movement, stepHeight);
		if (movement.y > 0.0 || !aboveGround(stepHeight)) return movement;

		Fall fall = (dx, dz) -> canFallAtLeast(dx, dz, stepHeight);
		return shorten(movement, fall);
	}

	private boolean aboveGround(float stepHeight) {
		if (body.isOnGround()) return true;
		if (body.has(EntityBehaviour.DOUBLE_FALL_DISTANCE))
			return body.getFallDistance() < stepHeight && !canFallAtLeast(0.0, 0.0, stepHeight - body.getFallDistance());

		float fallDistance = (float) body.getFallDistance();
		return fallDistance < stepHeight && !canFallAtLeast(0.0, 0.0, stepHeight - fallDistance);
	}

	private boolean canFallAtLeast(double dx, double dz, double distance) {
		Box box = body.getBox();
		if (body.has(ClientBehaviour.VECTOR_INPUT))
			return collisions.noCollision(new Box(
					new Vector(box.minX + 1.0E-7 + dx, box.minY - distance - 1.0E-7, box.minZ + 1.0E-7 + dz),
					new Vector(box.maxX - 1.0E-7 + dx, box.minY, box.maxZ - 1.0E-7 + dz)
			), body.context());

		return collisions.noCollision(new Box(new Vector(box.minX + dx, box.minY - distance - 1.0E-5F, box.minZ + dz),
				new Vector(box.maxX + dx, box.minY, box.maxZ + dz)), body.context());
	}

	/**
	 * The back-off before 1.20.5: the box moved down the step height must touch a block.
	 */
	private @NotNull Vector legacy(@NotNull Vector movement, float stepHeight) {
		float fallDistance = (float) body.getFallDistance();
		boolean aboveGround = body.isOnGround() || fallDistance < stepHeight
				&& !collisions.noCollision(body.getBox().move(0.0, fallDistance - stepHeight, 0.0), body.context());
		if (!aboveGround) return movement;

		Fall fall = (dx, dz) -> collisions.noCollision(body.getBox().move(dx, -stepHeight, dz), body.context());
		return shortenLegacy(movement, fall);
	}

	/**
	 * Shortens each component towards zero in steps of {@code 0.05} while the box could fall there, as from 1.20.5.
	 */
	private static @NotNull Vector shorten(@NotNull Vector movement, @NotNull Fall fall) {
		double dx = movement.x;
		double dz = movement.z;
		double stepX = Math.signum(dx) * STEP;
		double stepZ = Math.signum(dz) * STEP;
		while (dx != 0.0 && fall.can(dx, 0.0)) {
			if (Math.abs(dx) <= STEP) {
				dx = 0.0;
				break;
			}
			dx -= stepX;
		}
		while (dz != 0.0 && fall.can(0.0, dz)) {
			if (Math.abs(dz) <= STEP) {
				dz = 0.0;
				break;
			}
			dz -= stepZ;
		}
		while (dx != 0.0 && dz != 0.0 && fall.can(dx, dz)) {
			dx = Math.abs(dx) <= STEP ? 0.0 : dx - stepX;
			dz = Math.abs(dz) <= STEP ? 0.0 : dz - stepZ;
		}

		return new Vector(dx, movement.y, dz);
	}

	/**
	 * Shortens each component towards zero in steps of {@code 0.05}, as before 1.20.5.
	 */
	private static @NotNull Vector shortenLegacy(@NotNull Vector movement, @NotNull Fall fall) {
		double dx = movement.x;
		double dz = movement.z;
		while (dx != 0.0 && fall.can(dx, 0.0)) dx = towardsZero(dx);
		while (dz != 0.0 && fall.can(0.0, dz)) dz = towardsZero(dz);
		while (dx != 0.0 && dz != 0.0 && fall.can(dx, dz)) {
			dx = towardsZero(dx);
			dz = towardsZero(dz);
		}

		return new Vector(dx, movement.y, dz);
	}

	private static double towardsZero(double value) {
		if (value < STEP && value >= -STEP) return 0.0;

		return value > 0.0 ? value - STEP : value + STEP;
	}

	/**
	 * Whether the player's box moved horizontally could fall at the edge.
	 */
	@FunctionalInterface
	private interface Fall {
		boolean can(double dx, double dz);
	}
}
