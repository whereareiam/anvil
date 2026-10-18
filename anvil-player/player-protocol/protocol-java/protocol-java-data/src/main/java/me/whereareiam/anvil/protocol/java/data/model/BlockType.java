package me.whereareiam.anvil.protocol.java.data.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.data.type.BlockTrait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * One block of a release, such as {@code minecraft:stone}, with the facts that movement needs from it.
 *
 * <p>Its states have the consecutive global IDs {@link #getMinStateId()} to {@link #getMaxStateId()}. The name,
 * properties and states come from PrismarineJS minecraft-data; friction, the speed and jump factors, the bounce,
 * the stuck speed and the traits are vanilla's values that Anvil maintains by hand, and keep vanilla's defaults for
 * every block that does not change them.</p>
 */
@Value
@Builder
public class BlockType {
	/**
	 * The block's numeric ID in the release's block registry.
	 */
	int id;
	/**
	 * The namespaced name, such as {@code minecraft:oak_stairs}.
	 */
	@NotNull String name;
	/**
	 * The global ID of the block's first state.
	 */
	int minStateId;
	/**
	 * The global ID of the block's last state.
	 */
	int maxStateId;
	/**
	 * The global ID of the state a newly placed block has.
	 */
	int defaultStateId;
	/**
	 * The state properties, in the order that numbers the states.
	 */
	@Singular
	@NotNull List<BlockProperty> properties;
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
	@NotNull Set<BlockTrait> traits;

	/**
	 * Tests whether the block has a movement trait.
	 *
	 * @param trait trait to test
	 * @return whether the block has it
	 */
	public boolean hasTrait(@NotNull BlockTrait trait) {
		return traits.contains(trait);
	}

	/**
	 * The number of states the block has, the product of its properties' value counts.
	 *
	 * @return the state count, at least one
	 */
	public int stateCount() {
		return maxStateId - minStateId + 1;
	}
}
