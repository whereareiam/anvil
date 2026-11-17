package me.whereareiam.anvil.protocol.java.geometry;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable axis-aligned box in world coordinates with the operations of vanilla's {@code AABB} that entity physics uses,
 * computed in the same order.
 */
@ToString
@EqualsAndHashCode
public final class Box {
	/**
	 * The west face.
	 */
	public final double minX;
	/**
	 * The bottom face.
	 */
	public final double minY;
	/**
	 * The north face.
	 */
	public final double minZ;
	/**
	 * The east face.
	 */
	public final double maxX;
	/**
	 * The top face.
	 */
	public final double maxY;
	/**
	 * The south face.
	 */
	public final double maxZ;

	/**
	 * Creates a box between two corners, in either order, as {@code new AABB(...)} does.
	 *
	 * @param from one corner
	 * @param to the opposite corner
	 */
	public Box(@NotNull Vector from, @NotNull Vector to) {
		this.minX = Math.min(from.x, to.x);
		this.minY = Math.min(from.y, to.y);
		this.minZ = Math.min(from.z, to.z);
		this.maxX = Math.max(from.x, to.x);
		this.maxY = Math.max(from.y, to.y);
		this.maxZ = Math.max(from.z, to.z);
	}

	/**
	 * Builds an entity's box at a position, as {@code EntityDimensions.makeBoundingBox} does: half the width is a
	 * float, widened where it is added.
	 *
	 * @param position the position of the entity's feet
	 * @param width the entity's width
	 * @param height the entity's height
	 * @return the box
	 */
	public static @NotNull Box at(@NotNull Vector position, float width, float height) {
		float half = width / 2.0F;
		return new Box(new Vector(position.x - half, position.y, position.z - half),
				new Vector(position.x + half, position.y + height, position.z + half));
	}

	/**
	 * Builds a box of an entity's new size from this box's lower corner, as {@code Entity.refreshDimensions} does before
	 * 1.17 unless the entity narrows.
	 *
	 * @param width the entity's new width
	 * @param height the entity's new height
	 * @return the resized box
	 */
	public @NotNull Box resized(float width, float height) {
		return new Box(new Vector(minX, minY, minZ), new Vector(minX + width, minY + height, minZ + width));
	}

	/**
	 * Returns the lower face along an axis.
	 *
	 * @param axis the axis
	 * @return the face's coordinate
	 */
	public double min(@NotNull Axis axis) {
		return switch (axis) {
			case X -> minX;
			case Y -> minY;
			case Z -> minZ;
		};
	}

	/**
	 * Returns the upper face along an axis.
	 *
	 * @param axis the axis
	 * @return the face's coordinate
	 */
	public double max(@NotNull Axis axis) {
		return switch (axis) {
			case X -> maxX;
			case Y -> maxY;
			case Z -> maxZ;
		};
	}

	/**
	 * Moves the box.
	 *
	 * @param dx the east-west offset
	 * @param dy the vertical offset
	 * @param dz the north-south offset
	 * @return the moved box
	 */
	public @NotNull Box move(double dx, double dy, double dz) {
		return new Box(new Vector(minX + dx, minY + dy, minZ + dz), new Vector(maxX + dx, maxY + dy, maxZ + dz));
	}

	/**
	 * Moves the box.
	 *
	 * @param offset the offset
	 * @return the moved box
	 */
	public @NotNull Box move(@NotNull Vector offset) {
		return move(offset.x, offset.y, offset.z);
	}

	/**
	 * Stretches the box towards a movement, as {@code AABB.expandTowards} does: each face moves only in the movement's
	 * direction.
	 *
	 * @param dx the east-west movement
	 * @param dy the vertical movement
	 * @param dz the north-south movement
	 * @return the stretched box
	 */
	public @NotNull Box expandTowards(double dx, double dy, double dz) {
		return new Box(
				new Vector(dx < 0.0 ? minX + dx : minX, dy < 0.0 ? minY + dy : minY, dz < 0.0 ? minZ + dz : minZ),
				new Vector(dx > 0.0 ? maxX + dx : maxX, dy > 0.0 ? maxY + dy : maxY, dz > 0.0 ? maxZ + dz : maxZ)
		);
	}

	/**
	 * Stretches the box towards a movement.
	 *
	 * @param movement the movement
	 * @return the stretched box
	 */
	public @NotNull Box expandTowards(@NotNull Vector movement) {
		return expandTowards(movement.x, movement.y, movement.z);
	}

	/**
	 * Grows the box on every side.
	 *
	 * @param amount how far each face moves out
	 * @return the grown box
	 */
	public @NotNull Box inflate(double amount) {
		return new Box(new Vector(minX - amount, minY - amount, minZ - amount),
				new Vector(maxX + amount, maxY + amount, maxZ + amount));
	}

	/**
	 * Shrinks the box on every side.
	 *
	 * @param amount how far each face moves in
	 * @return the shrunk box
	 */
	public @NotNull Box deflate(double amount) {
		return inflate(-amount);
	}

	/**
	 * Returns the smallest box holding this box and another, as {@code AABB.minmax} does.
	 *
	 * @param other the other box
	 * @return the box around both
	 */
	public @NotNull Box union(@NotNull Box other) {
		return new Box(new Vector(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ)),
				new Vector(Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ)));
	}

	/**
	 * Tests whether another box overlaps this one with a volume, as {@code AABB.intersects} does: touching faces do
	 * not.
	 *
	 * @param other the other box
	 * @return whether they overlap
	 */
	public boolean intersects(@NotNull Box other) {
		return minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY
				&& minZ < other.maxZ && maxZ > other.minZ;
	}

	/**
	 * Returns the average edge length, as {@code AABB.getSize} computes it.
	 *
	 * @return the average edge length
	 */
	public double size() {
		return (maxX - minX + (maxY - minY) + (maxZ - minZ)) / 3.0;
	}
}
