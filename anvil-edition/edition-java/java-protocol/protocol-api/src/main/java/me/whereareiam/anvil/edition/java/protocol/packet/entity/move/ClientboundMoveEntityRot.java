package me.whereareiam.anvil.edition.java.protocol.packet.entity.move;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server turns an entity to a direction without moving it.
 */
@Value
@FieldNameConstants
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
