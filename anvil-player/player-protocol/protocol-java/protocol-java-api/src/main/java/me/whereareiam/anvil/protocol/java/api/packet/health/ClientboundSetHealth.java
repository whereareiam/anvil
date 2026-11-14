package me.whereareiam.anvil.protocol.java.api.packet.health;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The player's health, food level and saturation, which the server sends when one of them changes and when the player
 * joins or respawns. A health of zero or less means the player is dead.
 */
@Value
public class ClientboundSetHealth implements ClientboundPacket {
	/**
	 * Health in half hearts, {@code 20} for a full bar without bonus health.
	 */
	float health;
	/**
	 * Food level in half drumsticks, from {@code 0} to {@code 20}.
	 */
	int food;
	/**
	 * Food saturation, which drains before the food level does.
	 */
	float saturation;
}
