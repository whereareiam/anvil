package me.whereareiam.anvil.capability.vitals.model;

import lombok.Value;

/**
 * The player's health, food level and saturation at one moment, as its client knows them.
 */
@Value
public class VitalStatus {
	/**
	 * The values a client starts each connection with, until the server sends its own.
	 */
	public static final VitalStatus INITIAL = new VitalStatus(20F, 20, 5F);

	/**
	 * Health in half hearts; zero or less means the player is dead.
	 */
	float health;
	/**
	 * Food level in half drumsticks, from {@code 0} to {@code 20}.
	 */
	int food;
	/**
	 * Food saturation.
	 */
	float saturation;
}
