package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ChunkBatchPacing;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ValueTranslator;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.UnexpectedEncryptionException;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchStartPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;

import java.util.Objects;

/**
 * Answers the packets a client of the MCProtocolLib releases from 1.21.11 on must answer on its own, among them each
 * chunk batch with the rate {@link ChunkBatchPacing} wants next, and reports login and disconnects with their reason
 * flattened to plain text. The brand and the player's settings, its language among them, go out at the start of the
 * configuration through the release's {@link ClientConfiguration}. The login reports the profile the server granted in
 * its login success, which arrives before the game starts. Teleports are answered by the release's teleport listener,
 * which the segment's adapter hands to {@link NetworkClientSessions}.
 */
@RequiredArgsConstructor
final class ClientPacketListener extends SessionAdapter {
	private final ClientSession session;
	private final ClientListener<? super ClientSession> listener;
	private final PacketTable<Packet> packets;
	private final ComponentTranslator text;
	private final ChunkBatchPacing chunkBatches = new ChunkBatchPacing();
	private volatile ClientProfile granted;

	@Override
	public void connected(ConnectedEvent event) {
		// A server closes the connection right after its disconnect, so an answer the client sends just then, such as
		// the two that accept a teleport after a dimension change, may be written into a closed connection. Netty would
		// close the channel on that failed write and drop the disconnect still waiting to be read, reporting the end of
		// the stream instead of the server's reason. Without auto-close the failed write only shuts the output down,
		// and the channel closes when it reads the disconnect or the end of the stream.
		session.getChannel().config().setAutoClose(false);
	}

	@Override
	public void packetReceived(Session ignored, Packet packet) {
		if (packet instanceof ClientboundLoginFinishedPacket success) {
			granted = ClientProfile.builder().name(success.getProfile().getName()).uniqueId(success.getProfile().getId()).build();
			listener.granted(session, granted);
		}
		if (packet instanceof ClientboundLoginPacket)
			listener.loggedIn(session, Objects.requireNonNull(granted, "The server started the game before it granted a profile"));
		if (packet instanceof ClientboundChunkBatchStartPacket) chunkBatches.started();
		if (packet instanceof ClientboundChunkBatchFinishedPacket finished)
			session.send(new ServerboundChunkBatchReceivedPacket(chunkBatches.finished(finished.getBatchSize())));
		// A vanilla client answers every ping; NeoForge waits for the answer to tell a vanilla client from a modded
		// one.
		if (packet instanceof ClientboundPingPacket ping) session.send(new ServerboundPongPacket(ping.getId()));
		if (packet instanceof ClientboundDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, text.translate(disconnect.getReason()).plain());
		if (packet instanceof ClientboundLoginDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, text.translate(disconnect.getReason()).plain());

		ClientboundPacket translated = packets.decode(packet);
		if (translated != null) listener.received(session, translated);
	}

	@Override
	public void disconnected(DisconnectedEvent event) {
		String reason = event.getReason() == null ? "" : text.translate(event.getReason()).plain();
		listener.disconnected(session, cause(event), reason);
	}

	/**
	 * A close caused by the library refusing an encryption request means that the server wanted online
	 * authentication from a client that signed in with no account.
	 */
	private static DisconnectCause cause(DisconnectedEvent event) {
		return event.getCause() instanceof UnexpectedEncryptionException
				? DisconnectCause.AUTHENTICATION_REQUIRED
				: DisconnectCause.CONNECTION_LOST;
	}
}
