package me.whereareiam.anvil.player.library.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.player.library.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ConfigurationPackets;
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
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.EntityPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.InventoryPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.NetworkClientSessions;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.PlayerPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.SoundPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.TextTranslator;
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
	private static final ItemTranslator ITEMS = new ComponentItemTranslator(TEXT);

	/**
	 * Creates the client of the MCProtocolLib 26.1 release.
	 */
	public McProtocolClientAdapter() {
		super(new NetworkClientSessions(TEXT), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 26.1 release.
	 *
	 * @return the 1.21.11 translations with this release's interaction translation
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
