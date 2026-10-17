package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

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
	 * The change of the east-west coordinate, in blocks.
	 */
	double deltaX;
	/**
	 * The change of the height, in blocks.
	 */
	double deltaY;
	/**
	 * The change of the north-south coordinate, in blocks.
	 */
	double deltaZ;
	/**
	 * The horizontal direction the entity now faces, in degrees.
	 */
	float yaw;
	/**
	 * The vertical direction the entity now faces, in degrees.
	 */
	float pitch;
}
