package me.whereareiam.anvil.protocol.mcprotocol.client.translation;

import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Tells a session's translations which packets something wants, so that a translation whose packets are costly to
 * decode, such as a chunk column, skips them while nothing wants them. A segment's adapter creates each session's
 * {@link PacketTable} for the session's listener, which asks the player's protocol.
 *
 * <pre>{@code
 * if (!demand.wants(ClientboundLevelChunkWithLight.class)) return null;
 * return new ClientboundLevelChunkWithLight(position, sections(chunk));
 * }</pre>
 */
@FunctionalInterface
public interface PacketDemand {
	/**
	 * Wants every packet, as a table that only encodes and tells what its release carries does.
	 */
	@NotNull PacketDemand EVERY = type -> true;

	/**
	 * Tells whether something wants the packets of a class.
	 *
	 * @param type packet class
	 * @return whether a packet of the class should be decoded
	 */
	boolean wants(@NotNull Class<? extends ClientboundPacket> type);
}
