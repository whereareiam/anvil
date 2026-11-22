package me.whereareiam.anvil.edition.java.protocol.packet.inventory.container;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;

/**
 * The client acknowledges a rejected container action ({@link ClientboundContainerAck}), as the vanilla client of
 * releases before Minecraft 1.17 does between its ticks; the worker kit's client sends it itself, so a feature never
 * sends one.
 */
@Value
public class ServerboundContainerAck implements ServerboundPacket {
	/**
	 * The ID of the container, 0 for the player's inventory.
	 */
	int containerId;
	/**
	 * The action number of the rejected action.
	 */
	short uid;
	/**
	 * Whether the client accepts the rejection, which the vanilla client always does.
	 */
	boolean accepted;
}
