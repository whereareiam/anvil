package me.whereareiam.anvil.edition.java.data.block.model.collision;

import lombok.Value;

/**
 * How vanilla moves a block's shapes by a hash of their position ({@code BlockState.getOffset}): horizontally, and for
 * an {@code XYZ} offset also down, each axis by at most its largest offset.
 */
@Value
public class BlockOffset {
	/**
	 * Whether the shapes also move down ({@code OffsetType.XYZ}), not only horizontally ({@code XZ}).
	 */
	boolean vertical;
	/**
	 * The largest east-west and north-south offset, {@code getMaxHorizontalOffset} widened, as vanilla clamps to it.
	 */
	double maxHorizontal;
	/**
	 * The largest downward offset, {@code getMaxVerticalOffset} widened; {@code 0} for a horizontal offset.
	 */
	double maxVertical;
}
