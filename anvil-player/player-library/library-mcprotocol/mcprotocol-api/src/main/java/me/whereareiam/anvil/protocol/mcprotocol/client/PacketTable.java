package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Java Edition packets one MCProtocolLib release carries, translated area by area through the release's
 * {@link PacketTranslation}s. A segment's adapter lists the translations of its release; a translation may remember
 * what earlier packets of a connection said, such as the types of its entities, so each connection decodes through
 * a table of its own.
 *
 * <pre>{@code
 * PacketTable<Packet> packets = new PacketTable<>(List.of(new ChatPackets(), new InteractionPackets()));
 * session.send(packets.encode(new ServerboundChatCommand("spawn")));
 * }</pre>
 *
 * @param <P> the release's packet type
 */
public final class PacketTable<P> {
	private final List<PacketTranslation<P>> translations;
	private final Set<Class<? extends JavaPacket>> packets;

	/**
	 * Creates the table of a release from its translations.
	 *
	 * @param translations the release's translations, each carrying the packets of one area
	 * @throws IllegalArgumentException when two translations carry the same packet
	 */
	public PacketTable(@NotNull List<? extends PacketTranslation<P>> translations) {
		Map<Class<? extends JavaPacket>, PacketTranslation<P>> carriers = new HashMap<>();
		for (PacketTranslation<P> translation : translations) {
			for (Class<? extends JavaPacket> packet : translation.packets()) {
				PacketTranslation<P> carrier = carriers.putIfAbsent(packet, translation);
				if (carrier != null)
					throw new IllegalArgumentException(packet.getName() + " is carried by both "
							+ carrier.getClass().getName() + " and " + translation.getClass().getName());
			}
		}

		this.translations = List.copyOf(translations);
		this.packets = Set.copyOf(carriers.keySet());
	}

	/**
	 * Tells whether the release carries a Java Edition packet in either direction.
	 *
	 * @param type packet class
	 * @return whether a translation of the table carries it
	 */
	public boolean supports(@NotNull Class<? extends JavaPacket> type) {
		return packets.contains(type);
	}

	/**
	 * Translates a packet the release received.
	 *
	 * @param packet the release's packet
	 * @return the Java Edition packet, or {@code null} when the table carries no packet it translates to
	 */
	public @Nullable ClientboundPacket decode(@NotNull P packet) {
		for (PacketTranslation<P> translation : translations) {
			ClientboundPacket decoded = translation.decode(packet);
			if (decoded != null) return decoded;
		}

		return null;
	}

	/**
	 * Translates a packet to send.
	 *
	 * @param packet the Java Edition packet
	 * @return the release's packet
	 * @throws ProtocolUnavailableException when the release cannot carry the packet
	 */
	public @NotNull P encode(@NotNull ServerboundPacket packet) {
		for (PacketTranslation<P> translation : translations) {
			P encoded = translation.encode(packet);
			if (encoded != null) return encoded;
		}

		throw new ProtocolUnavailableException("the loaded " + McProtocolClient.LIBRARY_ID + " release does not carry "
				+ packet.getClass().getName());
	}
}
