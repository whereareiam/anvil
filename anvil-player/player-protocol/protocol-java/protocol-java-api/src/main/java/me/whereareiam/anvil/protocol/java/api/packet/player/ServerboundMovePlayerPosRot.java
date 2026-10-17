package me.whereareiam.anvil.protocol.java.api.packet.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The player's absolute position and view direction, sent together in one movement update.
 */
@Value
@Builder
public class ServerboundMovePlayerPosRot implements ServerboundPacket {
	/**
	 * The position of the player's feet.
	 */
	@NotNull Vec3d position;
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
