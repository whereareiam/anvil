package me.whereareiam.anvil.edition.java.protocol.packet.inventory.container;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;

/**
 * The server confirms or rejects a container action by the action number the client gave it. After a rejection the
 * server ignores further actions in that container until the client acknowledges it, which the vanilla client does
 * between its ticks, in the order its packets arrived, when the container is its inventory, ID 0, or the one it has open
 * ({@code ClientPacketListener.handleContainerAck}). Servers, such as anticheats, also send a rejection of an action
 * number the client never used to the inventory to learn which of their packets the client had handled, as they ping
 * later releases. The worker kit's client acknowledges every rejection itself with a {@link ServerboundContainerAck}.
 * Only releases before Minecraft 1.17 have the packet.
 */
@Value
@FieldNameConstants
public class ClientboundContainerAck implements ClientboundPacket {
	/**
	 * The ID of the container, 0 for the player's inventory.
	 */
	int containerId;
	/**
	 * The action number the server confirms or rejects.
	 */
	short uid;
	/**
	 * Whether the server performed the action as the client predicted.
	 */
	boolean accepted;
}
