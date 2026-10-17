package me.whereareiam.anvil.protocol.java.api.type;

/**
 * What a player does to an entity in an interaction packet, named as the game's interaction action types.
 */
public enum InteractAction {
	/**
	 * Interacts with the entity using a hand.
	 */
	INTERACT,
	/**
	 * Attacks the entity; Minecraft 26.1 and later send an attack in a packet of its own.
	 */
	ATTACK,
	/**
	 * Interacts with a point of the entity using a hand.
	 */
	INTERACT_AT
}
