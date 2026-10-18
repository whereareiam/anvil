package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.JavaClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ScreenPackets;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.21.11 release: the 1.21 {@link JavaClient} with the release's
 * network sessions and packet table, which keeps the 1.21 connection and screen translations.
 */
public final class McProtocolClientAdapter extends JavaClient<ClientSession> {
	/**
	 * Creates the client of the MCProtocolLib 1.21.11 release.
	 */
	public McProtocolClientAdapter() {
		super(new NetworkClientSessions(TeleportListener::new), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.21.11 release.
	 *
	 * @return the release's translations
	 */
	static PacketTable<Packet> packets() {
		return new PacketTable<>(List.of(
				new ChatPackets(),
				new CommandPackets(),
				new ConfigurationPackets(),
				new ConnectionPackets(),
				new CookiePackets(),
				new DialogPackets(),
				new EntityPackets(),
				new InteractionPackets(),
				new InventoryPackets(),
				new PlayerPackets(),
				new ScreenPackets(),
				new SoundPackets()
		));
	}
}
