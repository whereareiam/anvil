package me.whereareiam.anvil.protocol.java.api.packet.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;

/**
 * The player's view direction in a movement update that keeps the position.
 */
@Value
@Builder
public class ServerboundMovePlayerRot implements ServerboundPacket {
	/**
	 * The horizontal view direction in degrees.
	 */
	float yaw;
	/**
	 * The vertical view direction in degrees.
	 */
	float pitch;
	/**
	 * Whether the player stands on the ground.
	 */
	boolean onGround;
	/**
	 * Whether the player pushes against a block horizontally; releases before Minecraft 1.21.2 do not send it.
	 */
	boolean horizontalCollision;
}
