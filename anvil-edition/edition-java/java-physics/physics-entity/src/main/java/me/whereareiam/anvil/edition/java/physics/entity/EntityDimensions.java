package me.whereareiam.anvil.edition.java.physics.entity;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The size of an entity in one pose, as vanilla's {@code EntityDimensions} gives it: the width and height of its
 * bounding box and the height of its eyes above its feet.
 */
@Value
public class EntityDimensions {
	/**
	 * The width of the bounding box along both horizontal axes.
	 */
	float width;
	/**
	 * The height of the bounding box.
	 */
	float height;
	/**
	 * The height of the eyes above the feet.
	 */
	float eyeHeight;

	/**
	 * Scales the dimensions, as {@code EntityDimensions.scale} does for the scale attribute.
	 *
	 * @param factor the factor
	 * @return the scaled dimensions
	 */
	public @NotNull EntityDimensions scale(float factor) {
		return new EntityDimensions(width * factor, height * factor, eyeHeight * factor);
	}
}
