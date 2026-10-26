package me.whereareiam.anvil.capability.movement.java.physics;

import org.jetbrains.annotations.NotNull;

/**
 * An immutable axis-aligned box in world coordinates with the operations of vanilla's {@code AABB} that movement
 * uses, computed in the same order.
 */
final class Box {
	final double minX;
	final double minY;
	final double minZ;
	final double maxX;
	final double maxY;
	final double maxZ;

	Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		this.minX = Math.min(minX, maxX);
		this.minY = Math.min(minY, maxY);
		this.minZ = Math.min(minZ, maxZ);
		this.maxX = Math.max(minX, maxX);
		this.maxY = Math.max(minY, maxY);
		this.maxZ = Math.max(minZ, maxZ);
	}

	/**
	 * Builds an entity's box at a position, as {@code EntityDimensions.makeBoundingBox} does: half the width is a
	 * float, widened where it is added.
	 */
	static @NotNull Box at(double x, double y, double z, float width, float height) {
		float half = width / 2.0F;
		return new Box(x - half, y, z - half, x + half, y + height, z + half);
	}

	double min(@NotNull Axis axis) {
		return switch (axis) {
			case X -> minX;
			case Y -> minY;
			case Z -> minZ;
		};
	}

	double max(@NotNull Axis axis) {
		return switch (axis) {
			case X -> maxX;
			case Y -> maxY;
			case Z -> maxZ;
		};
	}

	@NotNull Box move(double dx, double dy, double dz) {
		return new Box(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
	}

	@NotNull Box move(@NotNull Vector offset) {
		return move(offset.x, offset.y, offset.z);
	}

	@NotNull Box expandTowards(double dx, double dy, double dz) {
		double x0 = minX;
		double y0 = minY;
		double z0 = minZ;
		double x1 = maxX;
		double y1 = maxY;
		double z1 = maxZ;
		if (dx < 0.0) x0 += dx;
		else if (dx > 0.0) x1 += dx;
		if (dy < 0.0) y0 += dy;
		else if (dy > 0.0) y1 += dy;
		if (dz < 0.0) z0 += dz;
		else if (dz > 0.0) z1 += dz;

		return new Box(x0, y0, z0, x1, y1, z1);
	}

	@NotNull Box expandTowards(@NotNull Vector offset) {
		return expandTowards(offset.x, offset.y, offset.z);
	}

	@NotNull Box inflate(double dx, double dy, double dz) {
		return new Box(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
	}

	@NotNull Box inflate(double amount) {
		return inflate(amount, amount, amount);
	}

	@NotNull Box deflate(double amount) {
		return inflate(-amount);
	}

	/**
	 * Tests whether two boxes overlap with a volume, as {@code AABB.intersects} does: touching faces do not.
	 */
	boolean intersects(double x0, double y0, double z0, double x1, double y1, double z1) {
		return minX < x1 && maxX > x0 && minY < y1 && maxY > y0 && minZ < z1 && maxZ > z0;
	}

	boolean intersects(@NotNull Box other) {
		return intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
	}

	/**
	 * The average edge length, as {@code AABB.getSize} computes it.
	 */
	double size() {
		return (maxX - minX + (maxY - minY) + (maxZ - minZ)) / 3.0;
	}

	@Override
	public String toString() {
		return "Box[" + minX + ", " + minY + ", " + minZ + " -> " + maxX + ", " + maxY + ", " + maxZ + "]";
	}
}
