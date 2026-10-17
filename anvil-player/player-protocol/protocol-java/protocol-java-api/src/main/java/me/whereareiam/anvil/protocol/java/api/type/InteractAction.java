package me.whereareiam.anvil.protocol.java.api.type;

/**
 * How a player interacts with an entity in an interaction packet, named as the game's interaction action types; an
 * attack is a packet of its own in the protocol.
 */
public enum InteractAction {
	/**
	 * Interacts with the entity using a hand.
	 */
	INTERACT,
	/**
	 * Interacts with a point of the entity using a hand.
	 */
	INTERACT_AT
}
