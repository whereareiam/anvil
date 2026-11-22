package me.whereareiam.anvil.edition.java.data.block.model;

import lombok.Value;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionCase;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionShape;
import me.whereareiam.anvil.edition.java.data.block.type.BlockFace;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One block state of a protocol, identified by its global ID as chunk and block update packets carry it.
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
	 * The state's collision shape for a player standing on it ({@link CollisionCase#STANDING}); empty for no
	 * collision. Where the block's {@link BlockType#getCollision() collision kind} is {@code OFFSET}, it is the shape
	 * before vanilla moves it by its position's offset; where it is {@code DYNAMIC}, {@link #getCollisionCases()} holds
	 * the shape of each other case that differs, and a block entity's shape is the one without the block entity.
	 */
	@NotNull CollisionShape collision;
	/**
	 * The fluid the state holds: the block's own fluid, or water where the state is waterlogged.
	 */
	@NotNull FluidType fluid;
	/**
	 * The collision shape of each player case whose shape differs from {@link #getCollision()}, as vanilla's
	 * {@code getCollisionShape} gives it; empty for a state whose shape holds for every case.
	 */
	@NotNull Map<CollisionCase, CollisionShape> collisionCases;
	/**
	 * The amount of the state's fluid, {@code FluidState.getAmount}: {@code 8} for a source or falling fluid, less as
	 * it flows away; {@code 0} without fluid.
	 */
	int fluidAmount;
	/**
	 * Whether the state's fluid is a source, {@code FluidState.isSource}.
	 */
	boolean fluidSource;
	/**
	 * Whether the state's fluid falls, {@code FlowingFluid.FALLING}.
	 */
	boolean fluidFalling;
	/**
	 * Whether the state stops a fluid beside it from flowing toward it or down beside it, as {@code FlowingFluid.getFlow}
	 * asks of its neighbours: its material blocks motion until 1.19.4, the state blocks motion
	 * ({@code BlockState.blocksMotion}) from 1.20, and the state is in the {@code minecraft:blocks_fluid_flow} tag from
	 * 26.3.
	 */
	boolean blocksFluidFlow;
	/**
	 * The faces of the state that are sturdy ({@code BlockState.isFaceSturdy}, without a block entity or neighbours),
	 * beside which a falling fluid flows down ({@code FlowingFluid.isSolidFace}, which leaves out ice).
	 */
	@NotNull Set<BlockFace> sturdyFaces;

	/**
	 * Returns the state's collision shape for a player case.
	 *
	 * @param context the player's case
	 * @return the shape vanilla gives that player
	 */
	public @NotNull CollisionShape collision(@NotNull CollisionCase context) {
		return collisionCases.getOrDefault(context, collision);
	}

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
		return block.getKind() == BlockKind.AIR;
	}
}
