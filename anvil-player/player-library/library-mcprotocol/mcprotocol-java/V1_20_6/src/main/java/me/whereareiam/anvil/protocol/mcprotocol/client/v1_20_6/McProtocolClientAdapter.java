package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.20.6 release: the {@link JavaClient} with the release's TCP
 * sessions and packet table.
 */
public final class McProtocolClientAdapter extends JavaClient<Session> {
	/**
	 * Creates the client of the MCProtocolLib 1.20.6 release.
	 */
	public McProtocolClientAdapter() {
		super(new TcpClientSessions(), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.20.6 release.
	 *
	 * @return the release's translations
	 */
	static PacketTable<Packet> packets() {
		ConnectionLevel level = new ConnectionLevel();
		return new PacketTable<>(List.of(
				new ChatPackets(),
				new CommandPackets(),
				new ConnectionPackets(level),
				new CookiePackets(),
				new EntityPackets(),
				new InteractionPackets(),
				new InventoryPackets(),
				new PlayerPackets(),
				new ScreenPackets(),
				new SoundPackets(),
				new WorldPackets(level)
		));
	}
}
