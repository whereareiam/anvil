package me.whereareiam.anvil.edition.java.protocol.type.interaction;

/**
 * What the server animates of an entity in an animate packet, named as the game's animate actions
 * ({@code ClientboundAnimatePacket}). Releases carry only the actions they have: the hurt animation until Minecraft
 * 1.19.4, which gave it a packet of its own, and the swings until 26.3, whose server sends them in a packet of their own.
 */
public enum AnimateAction {
	/**
	 * The entity swings its main hand's arm.
	 */
	SWING_MAIN_HAND,
	/**
	 * The entity flashes as it is hurt.
	 */
	HURT,
	/**
	 * The player wakes up and leaves its bed.
	 */
	WAKE_UP,
	/**
	 * The entity swings its off hand's arm.
	 */
	SWING_OFF_HAND,
	/**
	 * Critical-hit particles follow the entity.
	 */
	CRITICAL_HIT,
	/**
	 * Enchanted critical-hit particles follow the entity.
	 */
	MAGIC_CRITICAL_HIT
}
