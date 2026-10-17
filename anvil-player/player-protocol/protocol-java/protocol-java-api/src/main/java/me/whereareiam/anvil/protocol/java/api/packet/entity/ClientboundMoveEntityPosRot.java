package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server moves an entity by a short distance from where it is and turns it to a direction.
 */
@Value
@Builder
public class ClientboundMoveEntityPosRot implements ClientboundPacket {
	/**
	 * The entity's protocol ID.
	 */
	int entityId;
	/**
	 * The change of the entity's position, in blocks.
	 */
	@NotNull Vec3d delta;
	/**
	 * The horizontal direction the entity now faces, in degrees.
	 */
	float yaw;
	/**
	 * The vertical direction the entity now faces, in degrees.
	 */
	float pitch;
}
