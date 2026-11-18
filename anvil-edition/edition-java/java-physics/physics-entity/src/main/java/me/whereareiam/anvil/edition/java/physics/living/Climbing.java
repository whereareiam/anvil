package me.whereareiam.anvil.edition.java.physics.living;

import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import org.jetbrains.annotations.NotNull;

/**
 * Climbing, as {@code LivingEntity.onClimbable} and {@code handleOnClimbable} let a living entity climb: in a climbable
 * block, or an open trapdoor above a ladder that faces the same way, where the entity climbs at all
 * ({@link LivingBody#onClimbable}); there its speed is held to {@code 0.15} sideways and down, and an entity that holds
 * on ({@link LivingBody#isSuppressingSlidingDownLadder}) does not slide down, except in scaffolding.
 */
public final class Climbing {
	private final @NotNull LivingBody body;
	private final @NotNull BlockReader reader;

	/**
	 * Creates the climbing of a living entity.
	 *
	 * @param body the entity
	 */
	public Climbing(@NotNull LivingBody body) {
		this.body = body;
		this.reader = body.getLevel();
	}

	/**
	 * Tells whether a block is one an entity climbs in ({@code LivingEntity.onClimbable}, {@code trapdoorUsableAsLadder}).
	 *
	 * @param reader the blocks the entity moves among
	 * @param at the block's east-west, height and north-south coordinates
	 * @return whether it is climbable
	 */
	public static boolean isClimbable(@NotNull BlockReader reader, int @NotNull [] at) {
		BlockState state = reader.state(at[0], at[1], at[2]);
		if (state.getBlock().getMovement().has(MovementBehaviour.CLIMBABLE)) return true;
		if (state.getBlock().getKind() != BlockKind.TRAPDOOR || !"true".equals(state.property("open").orElse("false")))
			return false;

		BlockState below = reader.state(at[0], at[1] - 1, at[2]);
		return below.getBlock().getKind() == BlockKind.LADDER && below.property("facing").equals(state.property("facing"));
	}

	/**
	 * Holds the movement of a climbing entity ({@code handleOnClimbable}), which resets its fall distance.
	 *
	 * @param movement the movement
	 * @return the movement held to the climbing speeds, unchanged where the entity does not climb
	 */
	public @NotNull Vector handleOnClimbable(@NotNull Vector movement) {
		if (!body.onClimbable()) return movement;

		body.resetFallDistance();
		double x = Math.clamp(movement.x, -0.15F, 0.15F);
		double z = Math.clamp(movement.z, -0.15F, 0.15F);
		double y = Math.max(movement.y, -0.15F);
		if (y < 0.0 && !inScaffolding() && body.isSuppressingSlidingDownLadder()) y = 0.0;

		return new Vector(x, y, z);
	}

	private boolean inScaffolding() {
		int[] at = body.blockPosition();
		return reader.state(at[0], at[1], at[2]).getBlock().getKind() == BlockKind.SCAFFOLDING;
	}
}
