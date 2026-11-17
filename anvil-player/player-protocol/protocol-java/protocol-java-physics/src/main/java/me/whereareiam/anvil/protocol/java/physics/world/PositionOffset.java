package me.whereareiam.anvil.protocol.java.physics.world;

import me.whereareiam.anvil.protocol.java.block.model.collision.BlockOffset;
import me.whereareiam.anvil.protocol.java.geometry.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * How far vanilla moves the shapes of an offset block at a block column ({@code BlockState.getOffset}), by a hash of the
 * column ({@code Mth.getSeed(x, 0, z)}): its bits 0 to 3 move them east-west and its bits 8 to 11 north-south, each by
 * up to a quarter block either way and at most the block's largest horizontal offset, and for an {@code XYZ} offset its
 * bits 4 to 7 move them down by up to the block's largest vertical offset. Every release computes it so; the clients
 * before 1.17 move every offset block by up to a quarter block and down by up to {@code 0.2}, which their block data
 * records as each block's largest offsets.
 */
public final class PositionOffset {
	private PositionOffset() {
	}

	/**
	 * Returns the offset of a block's shapes at a block column.
	 *
	 * @param offset how the block moves its shapes
	 * @param x the column's east-west coordinate
	 * @param z the column's north-south coordinate
	 * @return the offset to add to the block's shapes
	 */
	public static @NotNull Vector at(@NotNull BlockOffset offset, int x, int z) {
		long seed = seed(x, 0, z);
		double max = offset.getMaxHorizontal();
		double offsetX = Math.clamp(((float) (seed & 15L) / 15.0F - 0.5) * 0.5, -max, max);
		double offsetY = offset.isVertical() ? ((float) (seed >> 4 & 15L) / 15.0F - 1.0) * offset.getMaxVertical() : 0.0;
		double offsetZ = Math.clamp(((float) (seed >> 8 & 15L) / 15.0F - 0.5) * 0.5, -max, max);
		return new Vector(offsetX, offsetY, offsetZ);
	}

	/**
	 * {@code Mth.getSeed}: the hash of a block position.
	 */
	private static long seed(int x, int y, int z) {
		long seed = x * 3129871 ^ z * 116129781L ^ y;
		seed = seed * seed * 42317861L + seed * 11L;
		return seed >> 16;
	}
}
