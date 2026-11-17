package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.BorderPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ChatPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ChunkPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.CommandPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ComponentItemTranslator;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConfigurationPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.CookiePackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.EffectPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.EntityPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.HealthPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.InventoryPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ItemTranslator;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.JavaClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.OverlayPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.PlayerPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.SoundPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.SynchronizedRegistries;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.TextTranslator;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.WorldPackets;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.21.1 release: the 1.20.6 {@link JavaClient} with the release's
 * TCP sessions, whose profiles and session service MCProtocolLib now brings itself, and the 1.20.6 packet table but
 * for the interaction, whose item use carries the view, and the attributes, whose modifiers are named by keys.
 */
public final class McProtocolClientAdapter extends JavaClient<Session> {
	private static final TextTranslator TEXT = new TextTranslator();
	private static final ItemTranslator ITEMS = new ComponentItemTranslator(TEXT);

	/**
	 * Creates the client of the MCProtocolLib 1.21.1 release.
	 */
	public McProtocolClientAdapter() {
		super(new TcpClientSessions(TEXT), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.21.1 release.
	 *
	 * @return the 1.20.6 translations with this release's interaction and attribute translations
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
