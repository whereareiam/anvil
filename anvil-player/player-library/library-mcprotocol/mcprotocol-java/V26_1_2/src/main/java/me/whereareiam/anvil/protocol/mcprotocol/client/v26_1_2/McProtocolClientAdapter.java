package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.JavaClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ScreenPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.ChatPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.CommandPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.ConfigurationPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.CookiePackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.DialogPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.EntityPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.InventoryPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.NetworkClientSessions;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.PlayerPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.SoundPackets;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 26.1 release: the 1.21 {@link JavaClient} with the 1.21.11
 * network sessions, whose sessions did not change, and the 1.21.11 packet table but for the interaction, which sends
 * an attack in a packet of its own.
 */
public final class McProtocolClientAdapter extends JavaClient<ClientSession> {
	/**
	 * Creates the client of the MCProtocolLib 26.1 release.
	 */
	public McProtocolClientAdapter() {
		super(new NetworkClientSessions(), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 26.1 release.
	 *
	 * @return the 1.21.11 translations with this release's interaction translation
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
