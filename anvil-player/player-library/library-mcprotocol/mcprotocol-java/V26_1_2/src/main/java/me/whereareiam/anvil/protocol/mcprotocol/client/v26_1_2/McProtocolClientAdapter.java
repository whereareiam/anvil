package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
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
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.TeleportListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.TextTranslator;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 26.1 release: the 1.21 {@link JavaClient} with the 1.21.11
 * network sessions, whose sessions did not change, and the 1.21.11 packet table but for the interaction, which sends
 * an attack in a packet of its own, the world, whose time carries world clocks, and the connection, whose dimension
 * types name their clock.
 */
public final class McProtocolClientAdapter extends JavaClient<ClientSession> {
	private static final TextTranslator TEXT = new TextTranslator();

	/**
	 * Creates the client of the MCProtocolLib 26.1 release.
	 */
	public McProtocolClientAdapter() {
		super(new NetworkClientSessions(TEXT, TeleportListener::new), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 26.1 release.
	 *
	 * @return the 1.21.11 translations with this release's interaction translation
	 */
	static PacketTable<Packet> packets() {
		// The connection and world translations read the dimension types and world clocks of the registry data before
		// the configuration translation decodes it.
		return new PacketTable<>(List.of(
				new ConnectionPackets(),
				new WorldPackets(),
				new ChatPackets(TEXT),
				new CommandPackets(),
				new ConfigurationPackets(),
				new CookiePackets(),
				new DialogPackets(),
				new EntityPackets(TEXT),
				new HealthPackets(TEXT),
				new InteractionPackets(),
				new InventoryPackets(TEXT),
				new PlayerPackets(),
				new ScreenPackets(TEXT),
				new SoundPackets()
		));
	}
}
