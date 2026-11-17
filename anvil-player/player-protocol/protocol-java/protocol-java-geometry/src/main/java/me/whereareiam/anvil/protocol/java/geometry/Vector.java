package me.whereareiam.anvil.protocol.java.geometry;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable vector of three doubles with the operations of vanilla's {@code Vec3} that entity physics uses, computed in
 * the same order so that the results are the same bits.
 */
@ToString
@EqualsAndHashCode
public final class Vector {
	/**
	 * The zero vector, {@code Vec3.ZERO}.
	 */
	public static final @NotNull Vector ZERO = new Vector(0.0, 0.0, 0.0);

	/**
	 * The east-west component.
	 */
	public final double x;
	/**
	 * The vertical component.
	 */
	public final double y;
	/**
	 * The north-south component.
	 */
	public final double z;

	/**
	 * Creates a vector.
	 *
	 * @param x the east-west component
	 * @param y the vertical component
	 * @param z the north-south component
	 */
	public Vector(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * Returns the component along an axis.
	 *
	 * @param axis the axis
	 * @return the component
	 */
	public double get(@NotNull Axis axis) {
		return switch (axis) {
			case X -> x;
			case Y -> y;
			case Z -> z;
		};
	}

	/**
	 * Returns this vector with one component replaced, as {@code Vec3.with} does.
	 *
	 * @param axis the component's axis
	 * @param value the new component
	 * @return the changed vector
	 */
	public @NotNull Vector with(@NotNull Axis axis, double value) {
		return switch (axis) {
			case X -> new Vector(value, y, z);
			case Y -> new Vector(x, value, z);
			case Z -> new Vector(x, y, value);
		};
	}

	/**
	 * Adds another vector.
	 *
	 * @param other the vector to add
	 * @return the sum
	 */
	public @NotNull Vector add(@NotNull Vector other) {
		return add(other.x, other.y, other.z);
	}

	/**
	 * Adds components.
	 *
	 * @param dx the east-west addend
	 * @param dy the vertical addend
	 * @param dz the north-south addend
	 * @return the sum
	 */
	public @NotNull Vector add(double dx, double dy, double dz) {
		return new Vector(x + dx, y + dy, z + dz);
	}

	/**
	 * Subtracts components, as {@code Vec3.subtract} adds their negation.
	 *
	 * @param dx the east-west subtrahend
	 * @param dy the vertical subtrahend
	 * @param dz the north-south subtrahend
	 * @return the difference
	 */
	public @NotNull Vector subtract(double dx, double dy, double dz) {
		return add(-dx, -dy, -dz);
	}

	/**
	 * Multiplies every component by one factor.
	 *
	 * @param factor the factor
	 * @return the scaled vector
	 */
	public @NotNull Vector scale(double factor) {
		return multiply(factor, factor, factor);
	}

	/**
	 * Multiplies each component by its own factor.
	 *
	 * @param fx the east-west factor
	 * @param fy the vertical factor
	 * @param fz the north-south factor
	 * @return the product
	 */
	public @NotNull Vector multiply(double fx, double fy, double fz) {
		return new Vector(x * fx, y * fy, z * fz);
	}

	/**
	 * Multiplies each component by the matching component of another vector.
	 *
	 * @param factors the factors
	 * @return the product
	 */
	public @NotNull Vector multiply(@NotNull Vector factors) {
		return multiply(factors.x, factors.y, factors.z);
	}

	/**
	 * Returns the squared length.
	 *
	 * @return the squared length
	 */
	public double lengthSqr() {
		return x * x + y * y + z * z;
	}

	/**
	 * Returns the squared length of the horizontal components.
	 *
	 * @return the squared horizontal length
	 */
	public double horizontalDistanceSqr() {
		return x * x + z * z;
	}

	/**
	 * Turns the vector about the east-west axis, as {@code Vec3.xRot} does.
	 *
	 * @param radians the angle
	 * @param trigonometry the client's sine and cosine
	 * @return the turned vector
	 */
	public @NotNull Vector xRot(float radians, @NotNull Trigonometry trigonometry) {
		float cos = trigonometry.cos(radians);
		float sin = trigonometry.sin(radians);
		return new Vector(x, y * cos + z * sin, z * cos - y * sin);
	}

	/**
	 * Turns the vector about the vertical axis, as {@code Vec3.yRot} does.
	 *
	 * @param radians the angle
	 * @param trigonometry the client's sine and cosine
	 * @return the turned vector
	 */
	public @NotNull Vector yRot(float radians, @NotNull Trigonometry trigonometry) {
		float cos = trigonometry.cos(radians);
		float sin = trigonometry.sin(radians);
		return new Vector(x * cos + z * sin, y, z * cos - x * sin);
	}
}
