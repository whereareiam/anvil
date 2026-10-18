package me.whereareiam.anvil.protocol.java.api.packet.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;

/**
 * A movement update that keeps the position and the view direction and tells only whether the player touches the
 * ground, as the client sends when the ground state changes without a move.
 */
@Value
@Builder
public class ServerboundMovePlayerStatusOnly implements ServerboundPacket {
	/**
	 * Whether the player stands on the ground.
	 */
	boolean onGround;
	/**
	 * Whether the player pushes against a block horizontally; releases before Minecraft 1.21.2 do not send it.
	 */
	boolean horizontalCollision;
}
