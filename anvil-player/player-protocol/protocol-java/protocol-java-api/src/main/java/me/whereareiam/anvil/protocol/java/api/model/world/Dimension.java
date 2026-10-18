package me.whereareiam.anvil.protocol.java.api.model.world;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The level the player is in, as the login and every respawn name it: the level's key and the height range its
 * dimension type gives it. Releases before Minecraft 1.17 have no such range; their levels span the 256 blocks from
 * height 0.
 */
@Value
public class Dimension {
	/**
	 * The level's key with its namespace, such as {@code minecraft:overworld} or {@code minecraft:the_nether}. The
	 * client keeps its chunks while it respawns into the level of the same key.
	 */
	@NotNull String key;
	/**
	 * The lowest height that holds blocks, a multiple of 16.
	 */
	int minY;
	/**
	 * The number of heights that hold blocks from {@link #minY} up, a multiple of 16.
	 */
	int height;

	/**
	 * Returns the number of 16-block sections each chunk column of the level has.
	 *
	 * @return the section count
	 */
	public int sectionCount() {
		return height >> 4;
	}

	/**
	 * Tells whether a height holds blocks in this level.
	 *
	 * @param y the height
	 * @return whether it lies from {@link #minY} up to below {@code minY + height}
	 */
	public boolean contains(int y) {
		return y >= minY && y < minY + height;
	}
}
