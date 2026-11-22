package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.UnexpectedEncryptionException;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchStartPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;

import java.util.Objects;
import java.util.function.Function;

/**
 * Answers the packets a client of the MCProtocolLib releases from 1.20.5 until 1.21.1 must answer on its own, among
 * them each chunk batch with the rate {@link ChunkBatchPacing} wants next, and reports login and disconnects
 * with their reason flattened to plain text. The login reports the profile the server granted in its login success,
 * which arrives before the game starts; each release's sessions read that profile, since MCProtocolLib 1.21 replaced
 * mcauthlib's profile with its own. The brand and the player's settings, its language among them, go out at the start
 * of the configuration through the release's {@link ConfigurationStart}. The session's {@link FramedClientSession}
 * hands the packets themselves over and then calls this listener on the channel's thread, in the order the packets
 * arrived, where the vanilla client answers a chunk batch too. What the client answers between its ticks, such as a
 * ping or a swing of its own player, the worker kit's client answers.
 */
@RequiredArgsConstructor
public final class ClientPacketListener extends SessionAdapter {
	/**
	 * The login the listener answers for.
	 */
	private final ClientLogin login;
	/**
	 * The receiver of the session's state.
	 */
	private final ClientListener<? super Session> listener;
	/**
	 * Reads the profile a login success grants, in the release's own profile type.
	 */
	private final Function<ClientboundGameProfilePacket, ClientProfile> profiles;
	/**
	 * Translates the release's text components.
	 */
	private final ComponentTranslator text;
	/**
	 * Paces the chunk batches the session acknowledges, as the vanilla client does.
	 */
	private final ChunkBatchPacing chunkBatches = new ChunkBatchPacing();
	private volatile ClientProfile granted;

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (packet instanceof ClientboundGameProfilePacket success) {
			granted = profiles.apply(success);
			listener.granted(session, granted);
		}
		if (packet instanceof ClientboundLoginPacket)
			listener.loggedIn(session, Objects.requireNonNull(granted, "The server started the game before it granted a profile"));
		if (packet instanceof ClientboundChunkBatchStartPacket) chunkBatches.started();
		if (packet instanceof ClientboundChunkBatchFinishedPacket finished)
			session.send(new ServerboundChunkBatchReceivedPacket(chunkBatches.finished(finished.getBatchSize())));
		if (packet instanceof ClientboundDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, text.translate(disconnect.getReason()).plain());
		if (packet instanceof ClientboundLoginDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, text.translate(disconnect.getReason()).plain());
	}

	/**
	 * MCProtocolLib announces the host it connects to; the network layer lets a listener replace a packet
	 * before it is written, which announces the virtual host without changing where the session connects.
	 */
	@Override
	public void packetSending(PacketSendingEvent event) {
		Packet packet = event.getPacket();
		String virtualHost = login.getConnection().getVirtualHost();
		if (virtualHost != null && packet instanceof ClientIntentionPacket intention)
			event.setPacket(intention.withHostname(virtualHost));
	}

	@Override
	public void disconnected(DisconnectedEvent event) {
		String reason = event.getReason() == null ? "" : text.translate(event.getReason()).plain();
		listener.disconnected(event.getSession(), cause(event), reason);
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
