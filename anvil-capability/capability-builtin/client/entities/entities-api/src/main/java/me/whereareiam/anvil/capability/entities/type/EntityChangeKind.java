package me.whereareiam.anvil.capability.entities.type;

/**
 * What a change did to an entity the player sees.
 */
public enum EntityChangeKind {
	/**
	 * The server spawned the entity for the player.
	 */
	APPEARED,
	/**
	 * What the entity shows, what it rides, what rides it, or whether it rides the player changed. Moves and turns
	 * are no change.
	 */
	CHANGED,
	/**
	 * The player no longer knows the entity: the server removed it, the player changed world, or the connection
	 * ended.
	 */
	DISAPPEARED
}
