package me.whereareiam.anvil.edition.java.protocol.packet.entity.move;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server moves an entity by a short distance from where it is and turns it to a direction.
 */
@Value
@FieldNameConstants
@Builder
public class ClientboundMoveEntityPosRot implements ClientboundPacket {
	/**
	 * The entity's protocol ID.
	 */
	@NotNull EntityId entityId;
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
