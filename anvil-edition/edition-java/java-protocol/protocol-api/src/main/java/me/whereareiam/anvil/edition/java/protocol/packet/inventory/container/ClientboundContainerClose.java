package me.whereareiam.anvil.edition.java.protocol.packet.inventory.container;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;

/**
 * The server closes a container the player has open, such as a chest a plugin opened as a menu. The client then shows
 * no container but the player's own inventory again.
 */
@Value
@FieldNameConstants
public class ClientboundContainerClose implements ClientboundPacket {
	/**
	 * ID of the closed container.
	 */
	int containerId;
}
