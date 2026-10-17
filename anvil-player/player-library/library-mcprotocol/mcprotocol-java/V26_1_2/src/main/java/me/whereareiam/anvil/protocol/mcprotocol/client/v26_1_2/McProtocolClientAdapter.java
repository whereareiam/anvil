package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.ChatPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.CommandPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.ConnectionPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.CookiePackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.DialogPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.InventoryPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.JavaClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.JavaPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.PlayerPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.ScreenPackets;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.SoundPackets;

import java.util.List;

/**
 * The client segment's adapter for MCProtocolLib 26.1: the 1.21.11 client, whose sessions did not change, with the
 * 1.21.11 packet table but for the interaction, which sends an attack in a packet of its own.
 */
public final class McProtocolClientAdapter extends JavaClient {
	/**
	 * Creates the client with the packet table of MCProtocolLib 26.1.
	 */
	public McProtocolClientAdapter() {
		super(packets());
	}

	/**
	 * Returns a new packet table of MCProtocolLib 26.1.
	 *
	 * @return the 1.21.11 areas with this release's interaction translation
	 */
	static JavaPackets packets() {
		return new JavaPackets(List.of(
				new ChatPackets(),
				new CommandPackets(),
				new ConnectionPackets(),
				new CookiePackets(),
				new DialogPackets(),
				new InteractionPackets(),
				new InventoryPackets(),
				new PlayerPackets(),
				new ScreenPackets(),
				new SoundPackets()
		));
	}
}
