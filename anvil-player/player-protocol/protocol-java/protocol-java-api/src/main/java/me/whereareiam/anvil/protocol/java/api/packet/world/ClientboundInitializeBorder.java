package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server tells the player the whole world border of its level, when the player joins it or respawns in it.
 */
@Value
@Builder
public class ClientboundInitializeBorder implements ClientboundPacket {
	/**
	 * The east-west coordinate of the border's center.
	 */
	double centerX;
	/**
	 * The north-south coordinate of the border's center.
	 */
	double centerZ;
	/**
	 * The border's side length in blocks when it starts moving, or its size while it stands still.
	 */
	double oldSize;
	/**
	 * The side length in blocks the border moves to, or its size while it stands still.
	 */
	double newSize;
	/**
	 * How long the border takes to move from the old to the new size, in ticks; {@code 0} while it stands still.
	 * Releases before Minecraft 1.21.9 send milliseconds, which every release reports as ticks of 50 milliseconds.
	 */
	long lerpTime;
	/**
	 * The largest side length the border may grow to.
	 */
	int absoluteMaxSize;
	/**
	 * How many blocks from the border the client starts tinting the screen.
	 */
	int warningBlocks;
	/**
	 * How many seconds before a moving border reaches the player the client starts tinting the screen.
	 */
	int warningTime;
}
