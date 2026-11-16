package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.BorderPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.HealthPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.InventoryPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.JavaClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.PlayerPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ScreenPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.TextTranslator;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.19.4 release: the 1.18.2 {@link JavaClient} with the release's
 * component disconnect reasons and packet table, which keeps the 1.18.2 inventory, player and screen translations.
 */
public final class McProtocolClientAdapter extends JavaClient {
	private static final TextTranslator TEXT = new TextTranslator();

	/**
	 * Creates the client of the MCProtocolLib 1.19.4 release.
	 */
	public McProtocolClientAdapter() {
		super(McProtocolClientAdapter::reason, TEXT, McProtocolClientAdapter::packets);
	}

	/**
	 * Reads the reason of a closed connection, which the release's packetlib keeps as a component.
	 *
	 * @param event the closed connection
	 * @return the reason flattened to plain text, or an empty text when the connection closed without one
	 */
	static String reason(DisconnectedEvent event) {
		return event.getReason() == null ? "" : TEXT.translate(event.getReason()).plain();
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.19.4 release.
	 *
	 * @param demand the packets the session wants decoded
	 * @return the release's translations
	 */
	static PacketTable<Packet> packets(PacketDemand demand) {
		ConnectionLevel level = new ConnectionLevel();
		return new PacketTable<>(List.of(
				new BorderPackets(),
				new ChatPackets(TEXT),
				new ChunkPackets(level, demand),
				new CommandPackets(),
				new ConnectionPackets(level),
				new EntityPackets(TEXT),
				new HealthPackets(TEXT),
				new InteractionPackets(),
				new InventoryPackets(TEXT),
				new PlayerPackets(),
				new ScreenPackets(TEXT),
				new SoundPackets(),
				new WorldPackets()
		));
	}
}
