package me.whereareiam.anvil.player.library.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.AttributePackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ChatPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ChunkPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.InteractionPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ItemTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.JavaClient;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.PlayerPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.SoundPackets;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.TextTranslator;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.17.1 release: the 1.16.5 {@link JavaClient} with the release's
 * client information and packet table, which keeps the 1.16.5 attribute, chat, interaction, player and sound
 * translations.
 */
public final class McProtocolClientAdapter extends JavaClient {
	private static final TextTranslator TEXT = new TextTranslator();
	private static final ItemTranslator ITEMS = new ItemTranslator(TEXT);

	/**
	 * Creates the client of the MCProtocolLib 1.17.1 release.
	 */
	public McProtocolClientAdapter() {
		super(new ReleaseClientInformation(), TEXT, McProtocolClientAdapter::packets);
	}

	/**
	 * Returns the protocol number of the MCProtocolLib 1.17.1 release, which the compiler copies from the release this
	 * adapter compiles against.
	 */
	@Override
	public int protocolNumber() {
		return MinecraftConstants.PROTOCOL_VERSION;
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.17.1 release.
	 *
	 * @param demand the packets the session wants decoded
	 * @return the release's translations
	 */
	static PacketTable<Packet> packets(PacketDemand demand) {
		return new PacketTable<>(List.of(
				new AttributePackets(),
				new BorderPackets(),
				new ChatPackets(TEXT),
				new ChunkPackets(demand),
				new CommandPackets(),
				new ConnectionPackets(),
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
