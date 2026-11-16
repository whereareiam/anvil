package me.whereareiam.anvil.protocol.java.api.model.world;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The level the player is in, as the login and every respawn name it: the level's key and its dimension type, which
 * gives the block heights the level spans.
 *
 * <pre>{@code
 * Level level = new Level("minecraft:overworld", DimensionType.known("minecraft:overworld"));
 * int sections = level.sectionCount(); // 24
 * }</pre>
 */
@Value
public class Level {
	/**
	 * The level's key with its namespace, such as {@code minecraft:overworld} or a plugin's {@code minecraft:arena}.
	 * The client keeps its chunks while it respawns into the level of the same key.
	 */
	@NotNull String key;
	/**
	 * The level's dimension type.
	 */
	@NotNull DimensionType type;

	/**
	 * Returns the lowest block height of the level.
	 *
	 * @return the lowest height, a multiple of 16
	 */
	public int minY() {
		return type.getMinY();
	}

	/**
	 * Returns the number of 16-block sections each chunk column of the level has.
	 *
	 * @return the section count
	 */
	public int sectionCount() {
		return type.getHeight() >> 4;
	}

	/**
	 * Tells whether a height holds blocks in this level.
	 *
	 * @param y the height
	 * @return whether it lies from the lowest height up to below the lowest height plus the level's height
	 */
	public boolean contains(int y) {
		return y >= type.getMinY() && y < type.getMinY() + type.getHeight();
	}
}
