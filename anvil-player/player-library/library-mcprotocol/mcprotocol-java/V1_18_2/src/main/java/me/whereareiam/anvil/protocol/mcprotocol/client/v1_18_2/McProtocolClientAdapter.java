package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;

import java.util.List;
import java.util.Objects;

/**
 * The client segment's adapter for the MCProtocolLib 1.18.2 release: the {@link JavaClient} with the release's
 * plain-string disconnect reasons and packet table.
 */
public final class McProtocolClientAdapter extends JavaClient {
	/**
	 * Creates the client of the MCProtocolLib 1.18.2 release.
	 */
	public McProtocolClientAdapter() {
		super(McProtocolClientAdapter::reason, McProtocolClientAdapter::packets);
	}

	/**
	 * Reads the reason of a closed connection, which packetlib 2.1 keeps as plain text.
	 *
	 * @param event the closed connection
	 * @return the reason, or an empty text when the connection closed without one
	 */
	static String reason(DisconnectedEvent event) {
		return Objects.requireNonNullElse(event.getReason(), "");
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.18.2 release.
	 *
	 * @return the release's translations
	 */
	static PacketTable<Packet> packets() {
		return new PacketTable<>(List.of(
				new ChatPackets(),
				new CommandPackets(),
				new ConnectionPackets(),
				new EntityPackets(),
				new InteractionPackets(),
				new InventoryPackets(),
				new PlayerPackets(),
				new ScreenPackets(),
				new SoundPackets()
		));
	}
}
