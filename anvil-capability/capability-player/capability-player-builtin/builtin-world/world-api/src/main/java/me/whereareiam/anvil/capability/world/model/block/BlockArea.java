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
 * assertEquals(new BlockPosition(4, 64, 2), room.getMin());
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
						Math.min(corner.getX(), opposite.getX()),
						Math.min(corner.getY(), opposite.getY()),
						Math.min(corner.getZ(), opposite.getZ())
				),
				new BlockPosition(
						Math.max(corner.getX(), opposite.getX()),
						Math.max(corner.getY(), opposite.getY()),
						Math.max(corner.getZ(), opposite.getZ())
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
		return block.getX() >= min.getX() && block.getX() <= max.getX()
				&& block.getY() >= min.getY() && block.getY() <= max.getY()
				&& block.getZ() >= min.getZ() && block.getZ() <= max.getZ();
	}
}
