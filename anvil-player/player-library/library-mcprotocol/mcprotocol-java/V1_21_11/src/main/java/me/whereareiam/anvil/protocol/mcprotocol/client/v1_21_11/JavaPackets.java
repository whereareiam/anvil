package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Java Edition packets a release carries, translated area by area. The table belongs to the segment's adapter,
 * which lists the areas of its release: a segment extending this one reuses the areas that did not change and brings
 * its own for those that did.
 */
public final class JavaPackets {
	private final List<PacketTranslation<Packet>> areas;
	private final Set<Class<? extends JavaPacket>> supported;

	/**
	 * Creates the table of a release from its areas, each translating the packets of one area of the game.
	 *
	 * @param areas the release's translations, none of which carries a packet another one carries
	 */
	public JavaPackets(@NotNull List<PacketTranslation<Packet>> areas) {
		this.areas = List.copyOf(areas);
		this.supported = this.areas.stream()
				.flatMap(area -> area.packets().stream())
				.collect(Collectors.toUnmodifiableSet());
	}

	boolean supports(@NotNull Class<? extends JavaPacket> type) {
		return supported.contains(type);
	}

	@Nullable ClientboundPacket decode(@NotNull Packet packet) {
		for (PacketTranslation<Packet> area : areas) {
			ClientboundPacket decoded = area.decode(packet);
			if (decoded != null) return decoded;
		}

		return null;
	}

	@NotNull Packet encode(@NotNull ServerboundPacket packet) {
		for (PacketTranslation<Packet> area : areas) {
			Packet encoded = area.encode(packet);
			if (encoded != null) return encoded;
		}

		throw new ProtocolUnavailableException("the loaded " + McProtocolClient.LIBRARY_ID + " release does not carry "
				+ packet.getClass().getName());
	}
}
