package me.whereareiam.anvil.capability.entities.packet.model;

import lombok.Builder;
import lombok.Value;

/**
 * An entity turned. Each angle, in degrees, is either absolute or added to the current one, as a teleport with
 * relative rotation says.
 */
@Value
@Builder
public class EntityTurned implements EntityPacket {
	int entityId;
	float yaw;
	float pitch;
	boolean relativeYaw;
	boolean relativePitch;
}
