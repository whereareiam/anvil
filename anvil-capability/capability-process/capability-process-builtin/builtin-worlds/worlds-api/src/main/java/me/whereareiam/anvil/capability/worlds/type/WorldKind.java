package me.whereareiam.anvil.capability.worlds.type;

/**
 * The kind of dimension a world is, as its server reports it, which decides how the world behaves: its sky, its
 * light and what portals lead to it.
 */
public enum WorldKind {
	/**
	 * A world that behaves like the overworld.
	 */
	OVERWORLD,
	/**
	 * A world that behaves like the nether.
	 */
	NETHER,
	/**
	 * A world that behaves like the end.
	 */
	END,
	/**
	 * A world whose dimension type is none of the vanilla ones, such as one a data pack or a mod adds.
	 */
	CUSTOM
}
