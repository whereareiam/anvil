package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;

import java.util.List;

/**
 * The client segment's adapter for the MCProtocolLib 1.16.5 release: the {@link JavaClient} with the release's answers
 * and packet table.
 */
public final class McProtocolClientAdapter extends JavaClient {
	private static final TextTranslator TEXT = new TextTranslator();

	/**
	 * Creates the client of the MCProtocolLib 1.16.5 release.
	 */
	public McProtocolClientAdapter() {
		super(new ReleaseAnswers(), TEXT, McProtocolClientAdapter::packets);
	}

	/**
	 * Returns the protocol number of the MCProtocolLib 1.16.5 release, which the compiler copies from the release this
	 * adapter compiles against.
	 */
	@Override
	public int protocolNumber() {
		return MinecraftConstants.PROTOCOL_VERSION;
	}

	/**
	 * Returns a new packet table of the MCProtocolLib 1.16.5 release.
	 *
	 * @return the release's translations
	 */
	static PacketTable<Packet> packets() {
		return new PacketTable<>(List.of(
				new ChatPackets(TEXT),
				new CommandPackets(),
				new ConnectionPackets(),
				new EntityPackets(TEXT),
				new InteractionPackets(),
				new InventoryPackets(TEXT),
				new PlayerPackets(),
				new ScreenPackets(TEXT),
				new SoundPackets()
		));
	}
}
