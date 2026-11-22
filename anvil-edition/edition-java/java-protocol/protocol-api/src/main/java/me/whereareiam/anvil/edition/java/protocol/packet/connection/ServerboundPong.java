package me.whereareiam.anvil.edition.java.protocol.packet.connection;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;

/**
 * The client answers a {@link ClientboundPing} with its ID, as the vanilla client does between its ticks. The worker
 * kit's client sends it itself for every ping, so a feature never sends one.
 */
@Value
public class ServerboundPong implements ServerboundPacket {
	/**
	 * The ID of the answered ping.
	 */
	int id;
}
