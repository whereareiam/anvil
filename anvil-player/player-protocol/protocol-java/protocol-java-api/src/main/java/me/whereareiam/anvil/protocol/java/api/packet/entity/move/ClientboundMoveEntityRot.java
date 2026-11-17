package me.whereareiam.anvil.protocol.java.api.packet.entity.move;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server turns an entity to a direction without moving it.
 */
@Value
public class ClientboundMoveEntityRot implements ClientboundPacket {
	/**
	 * The entity's protocol ID.
	 */
	@NotNull EntityId entityId;
	/**
	 * The horizontal direction the entity now faces, in degrees.
	 */
	float yaw;
	/**
	 * The vertical direction the entity now faces, in degrees.
	 */
	float pitch;
}
