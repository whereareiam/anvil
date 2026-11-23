package me.whereareiam.anvil.capability.world.model.block;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A box of blocks of the player's level, both corners included, which a
 * {@link me.whereareiam.anvil.capability.world.block.BlockWatch BlockWatch} follows. Creating one orders the corners,
 * so any two opposite corners describe the same area.
 *
 * <pre>{@code
 * BlockArea room = BlockArea.of(new BlockPosition(10, 64, 10), new BlockPosition(4, 70, 2));
 * assertEquals(new BlockPosition(4, 64, 2), room.min());
 * assertTrue(room.contains(new BlockPosition(7, 66, 5)));
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockArea {
	/**
	 * The corner with the smallest x, y and z.
	 */
	@NotNull BlockPosition min;
	/**
	 * The corner with the largest x, y and z.
	 */
	@NotNull BlockPosition max;

	/**
	 * Creates the area between two opposite corners, both included.
	 *
	 * @param corner one corner
	 * @param opposite the opposite corner
	 * @return the area
	 */
	public static @NotNull BlockArea of(@NotNull BlockPosition corner, @NotNull BlockPosition opposite) {
		return new BlockArea(
				new BlockPosition(
						Math.min(corner.x(), opposite.x()),
						Math.min(corner.y(), opposite.y()),
						Math.min(corner.z(), opposite.z())
				),
				new BlockPosition(
						Math.max(corner.x(), opposite.x()),
						Math.max(corner.y(), opposite.y()),
						Math.max(corner.z(), opposite.z())
				)
		);
	}

	/**
	 * Creates the area of one block.
	 *
	 * @param block the block's position
	 * @return the area holding only that block
	 */
	public static @NotNull BlockArea of(@NotNull BlockPosition block) {
		return new BlockArea(block, block);
	}

	/**
	 * Tells whether a block lies in the area.
	 *
	 * @param block the block's position
	 * @return whether it lies between the corners, both included
	 */
	public boolean contains(@NotNull BlockPosition block) {
		return block.x() >= min.x() && block.x() <= max.x()
				&& block.y() >= min.y() && block.y() <= max.y()
				&& block.z() >= min.z() && block.z() <= max.z();
	}
}
