package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.20.6 release: the {@link JavaClient} with the release's TCP
 * sessions and packet table.
 */
public final class McProtocolClientAdapter extends JavaClient<Session> {
	private static final TextTranslator TEXT = new TextTranslator();
	private static final ItemTranslator ITEMS = new ComponentItemTranslator(TEXT);

	/**
	 * Creates the client of the MCProtocolLib 1.20.6 release.
	 */
	public McProtocolClientAdapter() {
		super(new TcpClientSessions(TEXT), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.20.6 release.
	 *
	 * @param demand the packets the session wants decoded
	 * @return the release's translations
	 */
	static PacketTable<Packet> packets(PacketDemand demand) {
		SynchronizedRegistries registries = new SynchronizedRegistries();
		ConnectionLevel level = new ConnectionLevel();
		return new PacketTable<>(List.of(
				new AttributePackets(),
				new BorderPackets(),
				new ChatPackets(TEXT),
				new ChunkPackets(level, demand),
				new CommandPackets(),
				new ConfigurationPackets(registries),
				new ConnectionPackets(registries, level),
				new CookiePackets(),
				new EffectPackets(),
				new EntityPackets(TEXT, ITEMS),
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
