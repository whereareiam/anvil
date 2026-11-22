package me.whereareiam.anvil.testkit.physics.harness.block;

import lombok.Value;

/**
 * How a block's shapes move by a hash of their position: on which axes, and the largest distance on each, as the
 * client's {@code BlockState.getOffset} moves them at a block column whose hash puts both at their extremes.
 */
@Value
public class BlockOffset {
	/**
	 * Whether the offset also moves the shapes down ({@code OffsetType.XYZ}), not only horizontally ({@code XZ}).
	 */
	boolean vertical;

	/**
	 * The largest east-west and north-south offset, {@code getMaxHorizontalOffset} as vanilla clamps to it.
	 */
	double maxHorizontal;

	/**
	 * The largest downward offset, {@code getMaxVerticalOffset}; {@code 0} for a horizontal offset.
	 */
	double maxVertical;
}
