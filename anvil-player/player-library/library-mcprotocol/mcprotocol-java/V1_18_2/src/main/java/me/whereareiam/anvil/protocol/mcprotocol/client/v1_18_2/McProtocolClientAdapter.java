package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;

import java.util.List;
import java.util.Objects;

/**
 * The client segment's adapter for the MCProtocolLib 1.18.2 release: the {@link JavaClient} with the release's
 * plain-string disconnect reasons and packet table.
 */
public final class McProtocolClientAdapter extends JavaClient {
	private static final TextTranslator TEXT = new TextTranslator();
	private static final ItemTranslator ITEMS = new ItemTranslator(TEXT);

	/**
	 * Creates the client of the MCProtocolLib 1.18.2 release.
	 */
	public McProtocolClientAdapter() {
		super(McProtocolClientAdapter::reason, TEXT, McProtocolClientAdapter::packets);
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
				new EntityPackets(TEXT, ITEMS),
				new EntityStatePackets(),
				new HealthPackets(TEXT),
				new InteractionPackets(),
				new InventoryPackets(TEXT, ITEMS),
				new PlayerPackets(),
				new OverlayPackets(TEXT),
				new SoundPackets(),
				new WorldPackets()
		));
	}
}
