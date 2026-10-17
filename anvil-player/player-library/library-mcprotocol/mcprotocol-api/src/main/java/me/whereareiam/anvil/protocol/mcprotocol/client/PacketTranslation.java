package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Translates one area of the Java Edition protocol, such as chat, between its packets and the packets of one
 * MCProtocolLib release. A client segment carries one translation per area its release supports, and a later segment
 * reuses the translations of the segment it extends that still link against its release.
 *
 * @param <P> the release's packet type
 */
public interface PacketTranslation<P> {
	/**
	 * Returns the Java Edition packets this translation carries in either direction.
	 *
	 * @return packet classes
	 */
	@NotNull Set<Class<? extends JavaPacket>> packets();

	/**
	 * Translates a packet the release received.
	 *
	 * @param packet the release's packet
	 * @return the Java Edition packet, or {@code null} when the packet is not one of this area
	 */
	@Nullable ClientboundPacket decode(@NotNull P packet);

	/**
	 * Translates a packet to send.
	 *
	 * @param packet the Java Edition packet
	 * @return the release's packet, or {@code null} when the packet is not one of this area
	 */
	@Nullable P encode(@NotNull ServerboundPacket packet);
}
