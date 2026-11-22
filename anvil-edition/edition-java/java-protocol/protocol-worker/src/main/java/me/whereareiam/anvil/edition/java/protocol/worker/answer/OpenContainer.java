package me.whereareiam.anvil.edition.java.protocol.worker.answer;

import me.whereareiam.anvil.edition.java.protocol.PlayerInventoryLayout;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerClose;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundOpenScreen;

/**
 * The container the client's player has open, as the vanilla client keeps its {@code containerMenu}: its inventory
 * until the server opens a screen, the screen's container until the server closes it, whatever container the closing
 * packet names, and the inventory again for the new player of a login or a respawn.
 *
 * <p>Not thread-safe; its owner serializes packets.</p>
 */
final class OpenContainer {
	private int containerId = PlayerInventoryLayout.CONTAINER_ID;

	/**
	 * Follows a packet that opens or closes a container or makes a new player; other packets leave it.
	 *
	 * @param packet the received packet
	 */
	void apply(ClientboundPacket packet) {
		switch (packet) {
			case ClientboundOpenScreen open -> containerId = open.getContainerId();
			case ClientboundContainerClose ignored -> containerId = PlayerInventoryLayout.CONTAINER_ID;
			case ClientboundLogin ignored -> containerId = PlayerInventoryLayout.CONTAINER_ID;
			case ClientboundRespawn ignored -> containerId = PlayerInventoryLayout.CONTAINER_ID;
			default -> {
			}
		}
	}

	/**
	 * Tells whether a container is the player's inventory or the one it has open, as {@code handleContainerAck} finds
	 * the container it answers for.
	 *
	 * @param containerId the container's ID
	 * @return whether the client shows it
	 */
	boolean shows(int containerId) {
		return containerId == PlayerInventoryLayout.CONTAINER_ID || containerId == this.containerId;
	}
}
