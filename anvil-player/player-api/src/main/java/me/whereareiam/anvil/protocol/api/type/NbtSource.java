package me.whereareiam.anvil.protocol.api.type;

/**
 * Where the NBT of an NBT text component comes from.
 */
public enum NbtSource {
	/**
	 * A block entity at a position.
	 */
	BLOCK,
	/**
	 * The entities a selector finds.
	 */
	ENTITY,
	/**
	 * A command storage, by its key.
	 */
	STORAGE
}
