package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The level the player is in, with the range of heights that hold blocks in it.
 */
@Value
public class SeenDimension {
	/**
	 * The level's key with its namespace, such as {@code minecraft:overworld} or {@code minecraft:the_nether}.
	 */
	@NotNull String key;
	/**
	 * The lowest height that holds blocks, such as {@code -64} in an overworld since Minecraft 1.18.
	 */
	int minY;
	/**
	 * The number of heights that hold blocks from {@link #getMinY()} up.
	 */
	int height;

	/**
	 * Returns the highest height that holds blocks.
	 *
	 * @return {@code minY + height - 1}
	 */
	public int maxY() {
		return minY + height - 1;
	}

	/**
	 * Tells whether a height holds blocks in this level.
	 *
	 * @param y the height
	 * @return whether it lies from {@link #getMinY()} up to {@link #maxY()}
	 */
	public boolean contains(int y) {
		return y >= minY && y <= maxY();
	}
}
