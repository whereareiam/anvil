package me.whereareiam.anvil.edition.java.protocol.packet.connection;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;

/**
 * The server asks the client to answer with the same ID, in the game and, from Minecraft 1.20.2, in the configuration.
 * The vanilla client answers each ping on its own thread, between its ticks and in the order its packets arrived
 * ({@code ClientCommonPacketListenerImpl.handlePing}), so the answer tells a server, such as an anticheat that pings
 * before each teleport, which of its packets the client had handled by then; NeoForge waits for the answer to tell a
 * vanilla client from a modded one. The worker kit's client answers every ping itself with a {@link ServerboundPong}. Releases before Minecraft 1.17 have no ping; their servers send a
 * rejected transaction of the player's inventory to the same end ({@code ClientboundContainerAck}).
 */
@Value
@FieldNameConstants
public class ClientboundPing implements ClientboundPacket {
	/**
	 * The ID the client answers with.
	 */
	int id;
}
