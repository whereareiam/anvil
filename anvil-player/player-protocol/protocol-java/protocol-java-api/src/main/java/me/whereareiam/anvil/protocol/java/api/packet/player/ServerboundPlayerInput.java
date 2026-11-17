package me.whereareiam.anvil.protocol.java.api.packet.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;

/**
 * The movement keys the player holds, which the client sends whenever they change. Releases before Minecraft 1.21.2
 * have no such packet: their client tells sprinting and sneaking through {@link ServerboundPlayerCommand}.
 */
@Value
@Builder
public class ServerboundPlayerInput implements ServerboundPacket {
	/**
	 * Whether the player holds the key that walks forward.
	 */
	boolean forward;
	/**
	 * Whether the player holds the key that walks backward.
	 */
	boolean backward;
	/**
	 * Whether the player holds the key that strafes left.
	 */
	boolean left;
	/**
	 * Whether the player holds the key that strafes right.
	 */
	boolean right;
	/**
	 * Whether the player holds the jump key.
	 */
	boolean jump;
	/**
	 * Whether the player holds the sneak key.
	 */
	boolean shift;
	/**
	 * Whether the player holds the sprint key.
	 */
	boolean sprint;
}
