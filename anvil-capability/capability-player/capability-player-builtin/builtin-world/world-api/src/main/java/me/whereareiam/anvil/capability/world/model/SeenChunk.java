package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A chunk column the player's client has: the sixteen by sixteen blocks of the level, over its whole height, at one
 * chunk position.
 */
@Value
public class SeenChunk {
	/**
	 * The east-west chunk coordinate, the block coordinate divided by 16 and rounded down.
	 */
	int x;
	/**
	 * The north-south chunk coordinate, the block coordinate divided by 16 and rounded down.
	 */
	int z;

	/**
	 * Returns the column that holds a block.
	 *
	 * @param x the block's east-west coordinate
	 * @param z the block's north-south coordinate
	 * @return the column's position
	 */
	public static @NotNull SeenChunk containing(int x, int z) {
		return new SeenChunk(x >> 4, z >> 4);
	}

	/**
	 * Tells whether the column holds a block.
	 *
	 * @param x the block's east-west coordinate
	 * @param z the block's north-south coordinate
	 * @return whether the block lies in this column
	 */
	public boolean contains(int x, int z) {
		return x >> 4 == this.x && z >> 4 == this.z;
	}
}
