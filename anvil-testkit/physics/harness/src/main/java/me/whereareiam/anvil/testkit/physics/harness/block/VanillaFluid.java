package me.whereareiam.anvil.testkit.physics.harness.block;

import lombok.Value;

/**
 * The fluid state of a block state, {@code BlockState.getFluidState}: which fluid by vanilla's fluid tags, its amount
 * ({@code getAmount}), whether it is a source ({@code isSource}) and whether it falls ({@code FlowingFluid.FALLING}).
 */
@Value
public class VanillaFluid {
	/**
	 * No fluid.
	 */
	public static final VanillaFluid NONE = new VanillaFluid(Type.NONE, 0, false, false);

	Type type;
	int amount;
	boolean source;
	boolean falling;

	/**
	 * Which fluid a fluid state is, by the tag vanilla's movement asks: {@code FluidTags.WATER} or {@code LAVA}.
	 */
	public enum Type {
		NONE,
		WATER,
		LAVA
	}
}
