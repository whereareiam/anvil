package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server starts moving the world border of the player's level from one size to another.
 */
@Value
public class ClientboundSetBorderLerpSize implements ClientboundPacket {
	/**
	 * The border's side length in blocks when it starts moving.
	 */
	double oldSize;
	/**
	 * The side length in blocks the border moves to.
	 */
	double newSize;
	/**
	 * How long the border takes to move, in ticks. Releases before Minecraft 1.21.9 send milliseconds, which every
	 * release reports as ticks of 50 milliseconds.
	 */
	long lerpTime;
}
