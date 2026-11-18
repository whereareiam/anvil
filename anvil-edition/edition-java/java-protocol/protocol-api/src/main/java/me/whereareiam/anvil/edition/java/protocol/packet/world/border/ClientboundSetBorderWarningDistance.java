package me.whereareiam.anvil.edition.java.protocol.packet.world.border;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;

/**
 * The server changes how near the world border the client warns of it.
 */
@Value
@FieldNameConstants
public class ClientboundSetBorderWarningDistance implements ClientboundPacket {
	/**
	 * How many blocks from the border the client starts tinting the screen.
	 */
	int warningBlocks;
}
