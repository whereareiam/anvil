package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.JavaClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ScreenPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.ChatPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.CommandPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.CookiePackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.DialogPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.InventoryPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.NetworkClientSessions;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.PlayerPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.SoundPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2.WorldPackets;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 26.3 release: the chain's {@link JavaClient} with the 1.21.11
 * network sessions, which answer teleports through this release's {@link TeleportListener}, and the 26.1 packet table
 * with this release's configuration, connection, entity and interaction translations. The connection translation comes
 * first, since it remembers the dimension types of the registry data the configuration translation carries.
 */
public final class McProtocolClientAdapter extends JavaClient<ClientSession> {
	/**
	 * Creates the client of the MCProtocolLib 26.3 release.
	 */
	public McProtocolClientAdapter() {
		super(new NetworkClientSessions(TeleportListener::new), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 26.3 release.
	 *
	 * @return the 26.1 translations, its world translation included, with this release's configuration, connection,
	 * entity and interaction translations
	 */
	static PacketTable<Packet> packets() {
		ConnectionLevel level = new ConnectionLevel();
		return new PacketTable<>(List.of(
				new ConnectionPackets(level),
				new ChatPackets(),
				new CommandPackets(),
				new ConfigurationPackets(),
				new CookiePackets(),
				new DialogPackets(),
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
