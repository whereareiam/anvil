package me.whereareiam.anvil.edition.java.data.block.model.movement;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * How a block changes the movement of an entity on or in it, as vanilla's blocks declare it.
 *
 * <p>Each value is read from the release's vanilla client: the block's own getters, and what its methods do to a
 * player.</p>
 */
@Value
@Builder
public class BlockMovement {
	/**
	 * Slipperiness ({@code Block.getFriction()}): {@code 0.6F} by default, {@code 0.98F} for ice, {@code 0.989F} for
	 * blue ice and {@code 0.8F} for slime. Vanilla multiplies the horizontal speed of an entity on the block by it
	 * times {@code 0.91F} every tick.
	 */
	float friction;
	/**
	 * The factor of an entity's horizontal speed on or in the block ({@code Block.getSpeedFactor()}): {@code 1.0F}
	 * by default, {@code 0.4F} for soul sand and honey.
	 */
	float speedFactor;
	/**
	 * The factor of an entity's jump power on the block ({@code Block.getJumpFactor()}): {@code 1.0F} by default,
	 * {@code 0.5F} for honey.
	 */
	float jumpFactor;
	/**
	 * How much of its fall speed an entity landing on the block keeps, reversed, when it does not suppress bouncing:
	 * {@code 0} when the block stops it, {@code 1.0} for slime, {@code 0.66F} widened for beds. Vanilla scales it by
	 * {@code 0.8} for an entity that is not living.
	 */
	double bounceFactor;
	/**
	 * How the block slows an entity inside it, or {@code null} when it does not.
	 */
	@Nullable StuckSpeed stuckSpeed;
	/**
	 * The vanilla movement behaviours of the block that its numbers do not describe.
	 */
	@Singular
	@NotNull Set<MovementBehaviour> behaviours;

	/**
	 * Tests whether the block has a movement behaviour.
	 *
	 * @param behaviour behaviour to test
	 * @return whether the block has it
	 */
	public boolean has(@NotNull MovementBehaviour behaviour) {
		return behaviours.contains(behaviour);
	}
}
