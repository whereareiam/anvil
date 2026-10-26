package me.whereareiam.anvil.capability.movement.java.physics;

import org.jetbrains.annotations.NotNull;

/**
 * An immutable vector of three doubles with the operations of vanilla's {@code Vec3} that movement uses, computed in
 * the same order so that the results are the same bits.
 */
final class Vector {
	static final Vector ZERO = new Vector(0.0, 0.0, 0.0);

	final double x;
	final double y;
	final double z;

	Vector(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	double get(@NotNull Axis axis) {
		return switch (axis) {
			case X -> x;
			case Y -> y;
			case Z -> z;
		};
	}

	@NotNull Vector with(@NotNull Axis axis, double value) {
		return switch (axis) {
			case X -> new Vector(value, y, z);
			case Y -> new Vector(x, value, z);
			case Z -> new Vector(x, y, value);
		};
	}

	@NotNull Vector add(@NotNull Vector other) {
		return add(other.x, other.y, other.z);
	}

	@NotNull Vector add(double dx, double dy, double dz) {
		return new Vector(x + dx, y + dy, z + dz);
	}

	@NotNull Vector subtract(double dx, double dy, double dz) {
		return add(-dx, -dy, -dz);
	}

	@NotNull Vector scale(double factor) {
		return multiply(factor, factor, factor);
	}

	@NotNull Vector multiply(double fx, double fy, double fz) {
		return new Vector(x * fx, y * fy, z * fz);
	}

	@NotNull Vector multiply(@NotNull Vector factors) {
		return multiply(factors.x, factors.y, factors.z);
	}

	@NotNull Vector normalize() {
		double length = Math.sqrt(x * x + y * y + z * z);
		return length < 1.0E-5F ? ZERO : new Vector(x / length, y / length, z / length);
	}

	double length() {
		return Math.sqrt(x * x + y * y + z * z);
	}

	double lengthSqr() {
		return x * x + y * y + z * z;
	}

	double horizontalDistanceSqr() {
		return x * x + z * z;
	}

	/**
	 * Turns the vector about the X axis, as {@code Vec3.xRot} does.
	 */
	@NotNull Vector xRot(float radians, @NotNull Trigonometry trigonometry) {
		float cos = trigonometry.cos(radians);
		float sin = trigonometry.sin(radians);
		return new Vector(x, y * cos + z * sin, z * cos - y * sin);
	}

	/**
	 * Turns the vector about the Y axis, as {@code Vec3.yRot} does.
	 */
	@NotNull Vector yRot(float radians, @NotNull Trigonometry trigonometry) {
		float cos = trigonometry.cos(radians);
		float sin = trigonometry.sin(radians);
		return new Vector(x * cos + z * sin, y, z * cos - x * sin);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Vector vector
				&& Double.compare(x, vector.x) == 0
				&& Double.compare(y, vector.y) == 0
				&& Double.compare(z, vector.z) == 0;
	}

	@Override
	public int hashCode() {
		return Double.hashCode(x) * 961 + Double.hashCode(y) * 31 + Double.hashCode(z);
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ")";
	}
}
