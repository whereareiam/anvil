package me.whereareiam.anvil.protocol.java.api.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The sky a client draws over a dimension, as its dimension type names it.
 */
public enum Skybox {
	/**
	 * No sky, as in the nether.
	 */
	NONE("none"),
	/**
	 * The overworld's sky with a sun, a moon and clouds.
	 */
	OVERWORLD("overworld"),
	/**
	 * The end's dark sky.
	 */
	END("end");

	private final String serializedName;

	Skybox(String serializedName) {
		this.serializedName = serializedName;
	}

	/**
	 * Returns the skybox a dimension type's description names.
	 *
	 * @param serializedName the name, such as {@code end}
	 * @return the skybox, or {@code null} for a name Minecraft does not know
	 */
	public static @Nullable Skybox named(@NotNull String serializedName) {
		for (Skybox skybox : values())
			if (skybox.serializedName.equals(serializedName)) return skybox;

		return null;
	}
}
