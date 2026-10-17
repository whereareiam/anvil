package me.whereareiam.anvil.capability.entities.packet.model;

import lombok.Builder;
import lombok.Value;

/**
 * The player's position changed: the server placed it, or the client sent its own movement. Each coordinate is
 * either absolute or, where the server says so, added to the current one.
 */
@Value
@Builder
public class PlayerMoved implements EntityPacket {
	double x;
	double y;
	double z;
	boolean relativeX;
	boolean relativeY;
	boolean relativeZ;
}
