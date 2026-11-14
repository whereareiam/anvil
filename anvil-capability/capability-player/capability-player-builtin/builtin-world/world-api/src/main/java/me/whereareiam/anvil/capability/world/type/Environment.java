package me.whereareiam.anvil.capability.world.type;

/**
 * How the client renders a dimension, by its dimension type's sky and shading of block faces.
 */
public enum Environment {
	/**
	 * The overworld's sky with a sun, a moon and clouds.
	 */
	OVERWORLD,
	/**
	 * The nether's even shading without a sky.
	 */
	NETHER,
	/**
	 * The end's dark sky.
	 */
	END,
	/**
	 * Any other sky and shading, such as a nether's shading under the overworld's sky that a data pack describes, a
	 * mod's own effects, or a mod's type the client takes from a data pack it shares with the server.
	 */
	CUSTOM
}
