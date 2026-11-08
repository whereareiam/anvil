package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ChatPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.CommandPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.CookiePackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.EntityPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.InventoryPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.JavaClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.PlayerPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ScreenPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.SoundPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.TextTranslator;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.21.1 release: the 1.20.6 {@link JavaClient} with the release's
 * TCP sessions, whose profiles and session service MCProtocolLib now brings itself, and the 1.20.6 packet table but
 * for the interaction, whose item use carries the view.
 */
public final class McProtocolClientAdapter extends JavaClient<Session> {
	private static final TextTranslator TEXT = new TextTranslator();

	/**
	 * Creates the client of the MCProtocolLib 1.21.1 release.
	 */
	public McProtocolClientAdapter() {
		super(new TcpClientSessions(TEXT), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.21.1 release.
	 *
	 * @return the 1.20.6 translations with this release's interaction translation
	 */
	static PacketTable<Packet> packets() {
		return new PacketTable<>(List.of(
				new ChatPackets(TEXT),
				new CommandPackets(),
				new ConnectionPackets(),
				new CookiePackets(),
				new EntityPackets(TEXT),
				new InteractionPackets(),
				new InventoryPackets(TEXT),
				new PlayerPackets(),
				new ScreenPackets(TEXT),
				new SoundPackets()
		));
	}
}
