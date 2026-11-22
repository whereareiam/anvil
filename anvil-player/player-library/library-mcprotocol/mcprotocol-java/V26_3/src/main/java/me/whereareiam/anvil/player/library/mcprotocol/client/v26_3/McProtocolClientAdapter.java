package me.whereareiam.anvil.player.library.mcprotocol.client.v26_3;

import me.whereareiam.anvil.player.library.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.EffectPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ItemTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.JavaClient;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.OverlayPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.SynchronizedRegistries;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_1.AttributePackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.BorderPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.ChatPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.ChunkPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.CommandPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.ComponentItemTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.CookiePackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.DialogPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.InventoryPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.NetworkClientSessions;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.SoundPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.TextTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v26_1_2.ConnectionPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v26_1_2.HealthPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v26_1_2.WorldPackets;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 26.3 release: the chain's {@link JavaClient} with the 1.21.11
 * network sessions and the 26.1 packet table
 * with this release's configuration, entity, interaction and player translations.
 */
public final class McProtocolClientAdapter extends JavaClient<ClientSession> {
	private static final TextTranslator TEXT = new TextTranslator();
	private static final ItemTranslator ITEMS = new ComponentItemTranslator(TEXT);

	/**
	 * Creates the client of the MCProtocolLib 26.3 release.
	 */
	public McProtocolClientAdapter() {
		super(new NetworkClientSessions(TEXT), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 26.3 release.
	 *
	 * @return the 26.1 translations with this release's configuration, entity and interaction translations
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
				new DialogPackets(),
				new EffectPackets(),
				new EntityPackets(TEXT, ITEMS),
				new HealthPackets(TEXT),
				new InteractionPackets(),
				new InventoryPackets(TEXT, ITEMS),
				new PlayerPackets(),
				new OverlayPackets(TEXT),
				new SoundPackets(),
				new WorldPackets(registries)
		));
	}
}
