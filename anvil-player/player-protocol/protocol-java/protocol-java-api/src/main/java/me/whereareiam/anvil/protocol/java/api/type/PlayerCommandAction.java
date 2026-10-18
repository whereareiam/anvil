package me.whereareiam.anvil.protocol.java.api.type;

/**
 * A movement action the player starts or stops through a player command.
 */
public enum PlayerCommandAction {
	/**
	 * The player starts sneaking. Releases from Minecraft 1.21.6 tell sneaking only through the player input, so their
	 * player command carries no sneaking.
	 */
	PRESS_SHIFT_KEY,
	/**
	 * The player stops sneaking. Releases from Minecraft 1.21.6 tell sneaking only through the player input, so their
	 * player command carries no sneaking.
	 */
	RELEASE_SHIFT_KEY,
	/**
	 * The player starts sprinting.
	 */
	START_SPRINTING,
	/**
	 * The player stops sprinting.
	 */
	STOP_SPRINTING
}
