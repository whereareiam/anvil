package me.whereareiam.anvil.protocol.java.block.generator.facts;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.block.model.movement.BlockMovement;
import me.whereareiam.anvil.protocol.java.block.type.BlockKind;
import me.whereareiam.anvil.protocol.java.block.type.CollisionKind;
import me.whereareiam.anvil.protocol.java.block.type.FluidType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Everything {@code block-facts.toml} says about one block, its entries merged, with vanilla's defaults for what no
 * entry sets.
 */
@Value
@Builder
public class BlockFact {
	/**
	 * The facts of a block no entry names: vanilla's defaults.
	 */
	public static final @NotNull BlockFact DEFAULT = BlockFact.builder()
			.kind(BlockKind.OTHER)
			.collision(CollisionKind.FIXED)
			.fluid(FluidType.NONE)
			.movement(BlockMovement.builder().friction(0.6F).speedFactor(1.0F).jumpFactor(1.0F).build())
			.propertyStarts(Map.of())
			.build();

	/**
	 * What the block is where vanilla's movement code asks.
	 */
	@NotNull BlockKind kind;
	/**
	 * How vanilla decides its collision shape.
	 */
	@NotNull CollisionKind collision;
	/**
	 * The fluid every state holds, {@link FluidType#NONE} when only waterlogged states hold water.
	 */
	@NotNull FluidType fluid;
	/**
	 * How the block changes an entity's movement.
	 */
	@NotNull BlockMovement movement;
	/**
	 * The lowest value of each integer property that does not start at {@code 0}.
	 */
	@NotNull Map<String, Integer> propertyStarts;
}
