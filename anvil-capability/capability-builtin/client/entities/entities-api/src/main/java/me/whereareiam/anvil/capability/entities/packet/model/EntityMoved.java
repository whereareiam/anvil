package me.whereareiam.anvil.capability.entities.packet.model;

import lombok.Builder;
import lombok.Value;

/**
 * An entity moved; a packet that also turns it is reported as an {@link EntityTurned} as well. Each coordinate is either absolute or added to the current one, as a relative move or a
 * teleport with relative coordinates says.
 */
@Value
@Builder
public class EntityMoved implements EntityPacket {
	int entityId;
	double x;
	double y;
	double z;
	boolean relativeX;
	boolean relativeY;
	boolean relativeZ;
}
