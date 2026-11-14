package me.whereareiam.anvil.protocol.java.api.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How a client shades the faces of blocks in a dimension, as its dimension type names it.
 */
public enum CardinalLight {
	/**
	 * The shading of the overworld and the end.
	 */
	DEFAULT("default"),
	/**
	 * The nether's shading, which lights every side alike.
	 */
	NETHER("nether");

	private final String serializedName;

	CardinalLight(String serializedName) {
		this.serializedName = serializedName;
	}

	/**
	 * Returns the shading a dimension type's description names.
	 *
	 * @param serializedName the name, such as {@code nether}
	 * @return the shading, or {@code null} for a name Minecraft does not know
	 */
	public static @Nullable CardinalLight named(@NotNull String serializedName) {
		for (CardinalLight light : values())
			if (light.serializedName.equals(serializedName)) return light;

		return null;
	}
}
