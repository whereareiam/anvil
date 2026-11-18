package me.whereareiam.anvil.edition.java.protocol.packet.player.move;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The player's absolute position in a movement update that keeps the view direction.
 */
@Value
@Builder
public class ServerboundMovePlayerPos implements ServerboundPacket {
	/**
	 * The position of the player's feet.
	 */
	@NotNull Vec3d position;
	/**
	 * Whether the player stands on the ground.
	 */
	boolean onGround;
	/**
	 * Whether the player pushes against a block horizontally; releases before Minecraft 1.21.2 do not send it.
	 */
	boolean horizontalCollision;
}
