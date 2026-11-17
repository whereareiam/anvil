package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.java.physics.world.BlockReader;
import me.whereareiam.anvil.protocol.java.block.model.BlockState;
import me.whereareiam.anvil.protocol.java.block.type.BlockKind;
import me.whereareiam.anvil.protocol.java.block.type.MovementBehaviour;
import org.jetbrains.annotations.NotNull;

/**
 * Climbing, as {@code LivingEntity.onClimbable} and {@code handleOnClimbable} let the player climb: in a climbable
 * block, or an open trapdoor above a ladder that faces the same way, unless it spectates or flies; there its speed is
 * held to {@code 0.15} sideways and down, and a player that holds sneak does not slide down, except in scaffolding.
 */
final class Climbing {
	private final @NotNull PlayerBody body;
	private final @NotNull BlockReader reader;

	Climbing(@NotNull PlayerBody body, @NotNull BlockReader reader) {
		this.body = body;
		this.reader = reader;
	}

	/**
	 * Tells whether the player climbs where it is ({@code onClimbable}).
	 */
	boolean onClimbable() {
		if (body.getServer().isSpectator() || body.getServer().isFlying()) return false;

		int[] at = body.blockPosition();
		BlockState state = reader.state(at[0], at[1], at[2]);
		if (state.getBlock().getMovement().has(MovementBehaviour.CLIMBABLE)) return true;
		if (state.getBlock().getKind() != BlockKind.TRAPDOOR || !"true".equals(state.property("open").orElse("false")))
			return false;

		BlockState below = reader.state(at[0], at[1] - 1, at[2]);
		return below.getBlock().getKind() == BlockKind.LADDER && below.property("facing").equals(state.property("facing"));
	}

	/**
	 * Holds the movement of a climbing player ({@code handleOnClimbable}), which resets its fall distance.
	 */
	@NotNull Vector handleOnClimbable(@NotNull Vector movement) {
		if (!onClimbable()) return movement;

		body.resetFallDistance();
		double x = Math.clamp(movement.x, -0.15F, 0.15F);
		double z = Math.clamp(movement.z, -0.15F, 0.15F);
		double y = Math.max(movement.y, -0.15F);
		if (y < 0.0 && !inScaffolding() && body.getKeys().isSneak()) y = 0.0;

		return new Vector(x, y, z);
	}

	private boolean inScaffolding() {
		int[] at = body.blockPosition();
		return reader.state(at[0], at[1], at[2]).getBlock().getKind() == BlockKind.SCAFFOLDING;
	}
}
