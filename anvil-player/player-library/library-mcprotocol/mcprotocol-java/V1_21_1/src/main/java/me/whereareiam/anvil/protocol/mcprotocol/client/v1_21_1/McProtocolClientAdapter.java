package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
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
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.WorldPackets;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.21.1 release: the 1.20.6 {@link JavaClient} with the release's
 * TCP sessions, whose profiles and session service MCProtocolLib now brings itself, and the 1.20.6 packet table but
 * for the interaction, whose item use carries the view.
 */
public final class McProtocolClientAdapter extends JavaClient<Session> {
	/**
	 * Creates the client of the MCProtocolLib 1.21.1 release.
	 */
	public McProtocolClientAdapter() {
		super(new TcpClientSessions(), McProtocolClientAdapter::packets);
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.21.1 release.
	 *
	 * @return the 1.20.6 translations with this release's interaction translation
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
