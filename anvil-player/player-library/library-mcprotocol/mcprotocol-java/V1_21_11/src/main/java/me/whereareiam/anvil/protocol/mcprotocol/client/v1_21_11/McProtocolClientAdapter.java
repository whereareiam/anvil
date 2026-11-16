package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConfigurationPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.HealthPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.JavaClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ScreenPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.SynchronizedRegistries;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.21.11 release: the 1.21 {@link JavaClient} with the release's
 * network sessions and packet table, which keeps the 1.21 connection and screen translations.
 */
public final class McProtocolClientAdapter extends JavaClient<ClientSession> {
	private static final TextTranslator TEXT = new TextTranslator();

	/**
	 * Creates the client of the MCProtocolLib 1.21.11 release.
	 */
	public McProtocolClientAdapter() {
		super(new NetworkClientSessions(TEXT, ReleaseAnswers::new), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.21.11 release.
	 *
	 * @param demand the packets the session wants decoded
	 * @return the release's translations
	 */
	static PacketTable<Packet> packets(PacketDemand demand) {
		SynchronizedRegistries registries = new SynchronizedRegistries();
		ConnectionLevel level = new ConnectionLevel();
		return new PacketTable<>(List.of(
				new BorderPackets(),
				new ChatPackets(TEXT),
				new ChunkPackets(level, demand),
				new CommandPackets(),
				new ConfigurationPackets(registries),
				new ConnectionPackets(registries, level),
				new CookiePackets(),
				new DialogPackets(),
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
