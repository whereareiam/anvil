package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.api.model.player.PayloadChannel;
import me.whereareiam.anvil.player.library.mcprotocol.client.neoforge.NeoForgeHandshake;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Answers NeoForge's configuration for a NeoForge client of the MCProtocolLib 1.21.1 release: every custom payload the
 * server sends goes through the {@link NeoForgeHandshake}, and its answer goes back on the channel it names. The server
 * asks for the client's channels before it pings the client, and takes a pong without that answer for a vanilla
 * client, so the answer leaves as soon as the question arrives, before the library answers the ping.
 */
final class NeoForgeConfiguration extends SessionAdapter {
	private final NeoForgeHandshake handshake;

	/**
	 * Creates the answers of a client that claims the given channels.
	 *
	 * @param channels the payload channels the client claims
	 */
	NeoForgeConfiguration(@NotNull List<PayloadChannel> channels) {
		this.handshake = new NeoForgeHandshake(channels);
	}

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (!(packet instanceof ClientboundCustomPayloadPacket payload)) return;

		handshake.answer(payload.getChannel().asString(), payload.getData())
				.ifPresent(answer -> session.send(new ServerboundCustomPayloadPacket(Key.key(answer.channel()), answer.payload())));
	}
}
