package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server changes how near the world border the client warns of it.
 */
@Value
public class ClientboundSetBorderWarningDistance implements ClientboundPacket {
	/**
	 * How many blocks from the border the client starts tinting the screen.
	 */
	int warningBlocks;
}
