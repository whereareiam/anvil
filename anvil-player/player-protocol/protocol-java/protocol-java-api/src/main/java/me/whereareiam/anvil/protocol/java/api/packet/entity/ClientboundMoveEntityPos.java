package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server moves an entity by a short distance from where it is.
 */
@Value
@Builder
public class ClientboundMoveEntityPos implements ClientboundPacket {
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
}
