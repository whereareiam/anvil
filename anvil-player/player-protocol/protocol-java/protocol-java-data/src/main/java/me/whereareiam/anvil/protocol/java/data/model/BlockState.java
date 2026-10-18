package me.whereareiam.anvil.protocol.java.data.model;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.data.type.BlockTrait;
import me.whereareiam.anvil.protocol.java.data.type.FluidType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * One block state of a release, identified by its global ID as chunk and block update packets carry it.
 *
 * <pre>{@code
 * BlockState state = registry.state(stateId);
 * boolean solid = !state.getCollision().isEmpty();
 * boolean wet = state.getFluid() == FluidType.WATER;
 * }</pre>
 */
@Value
public class BlockState {
	/**
	 * The global block state ID.
	 */
	int id;
	/**
	 * The block the state belongs to.
	 */
	@NotNull BlockType block;
	/**
	 * The value of each of the block's properties, in the block's property order.
	 */
	@NotNull Map<String, String> properties;
	/**
	 * The state's collision shape; empty for no collision.
	 */
	@NotNull CollisionShape collision;
	/**
	 * The fluid the state holds: the block's own fluid, or water where the state is waterlogged.
	 */
	@NotNull FluidType fluid;

	/**
	 * Returns the value of one property.
	 *
	 * @param name property name, such as {@code half}
	 * @return the value, or empty when the block has no such property
	 */
	public @NotNull Optional<String> property(@NotNull String name) {
		return Optional.ofNullable(properties.get(name));
	}

	/**
	 * Tests whether the state is air, which chunks leave out and entities move through.
	 *
	 * @return whether the block is {@code air}, {@code cave_air} or {@code void_air}
	 */
	public boolean isAir() {
		return block.hasTrait(BlockTrait.AIR);
	}
}
