package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ConnectionPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.InventoryPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.JavaClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.PlayerPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ScreenPackets;

import java.util.List;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ValueTranslator;

/**
 * The client segment's adapter for the MCProtocolLib 1.19.4 release: the 1.18.2 {@link JavaClient} with the release's
 * component disconnect reasons and packet table, which keeps the 1.18.2 connection, inventory, player and screen
 * translations.
 */
public final class McProtocolClientAdapter extends JavaClient {
	/**
	 * Creates the client of the MCProtocolLib 1.19.4 release.
	 */
	public McProtocolClientAdapter() {
		super(McProtocolClientAdapter::reason, McProtocolClientAdapter::packets);
	}

	/**
	 * Reads the reason of a closed connection, which the release's packetlib keeps as a component.
	 *
	 * @param event the closed connection
	 * @return the reason flattened to plain text, or an empty text when the connection closed without one
	 */
	static String reason(DisconnectedEvent event) {
		return event.getReason() == null ? "" : ValueTranslator.plain(event.getReason());
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.19.4 release.
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
