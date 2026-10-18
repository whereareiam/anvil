package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.handshake.client.HandshakePacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.login.server.EncryptionRequestPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginSuccessPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.PacketSendingEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;

import java.util.Objects;

/**
 * Answers the packets a client of the MCProtocolLib releases from 1.16.5 until 1.17.1 must answer on its own and
 * reports login, teleports and disconnects with their reason flattened to plain text. The login reports the profile the
 * server granted in its login success, which arrives before the game starts; the release's {@link ClientAnswers} build
 * the client information and acknowledge what the release must acknowledge.
 *
 * <p>The session adds MCProtocolLib's own listener when it connects, after this one, so this listener sees an
 * encryption request first. These releases refuse the request of a client without an account with an exception that
 * names nothing, so a request received without credentials marks the following disconnect as one caused by the server
 * wanting online authentication.</p>
 */
@RequiredArgsConstructor
final class ClientPacketListener extends SessionAdapter {
	private final ClientLogin login;
	private final ClientListener<? super Session> listener;
	private final PacketTable<Packet> packets;
	private final ClientAnswers answers;
	private volatile ClientProfile granted;
	private volatile boolean authenticationRequired;

	@Override
	public void packetReceived(PacketReceivedEvent event) {
		Session session = event.getSession();
		Packet packet = event.getPacket();
		if (packet instanceof EncryptionRequestPacket && login.getCredentials() == null) authenticationRequired = true;
		if (packet instanceof LoginSuccessPacket success)
			granted = ClientProfile.builder()
					.name(success.getProfile().getName())
					.uniqueId(success.getProfile().getId())
					.build();
		if (packet instanceof ServerJoinGamePacket) {
			session.send(answers.clientInformation(login.getSettings()));
			listener.loggedIn(session,
					Objects.requireNonNull(granted, "The server started the game before it granted a profile"));
		}
		if (packet instanceof ServerPlayerPositionRotationPacket position) {
			listener.teleported(session, position.getYaw(), position.getPitch());
			session.send(new ClientTeleportConfirmPacket(position.getTeleportId()));
		}
		if (packet instanceof ServerDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, ValueTranslator.plain(disconnect.getReason()));
		if (packet instanceof LoginDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, ValueTranslator.plain(disconnect.getReason()));
		Packet answer = answers.answer(packet);
		if (answer != null) session.send(answer);

		ClientboundPacket translated = packets.decode(packet);
		if (translated != null) listener.received(session, translated);
	}

	/**
	 * MCProtocolLib announces the host it connects to; packetlib lets a listener replace a packet before it is written,
	 * which announces the virtual host without changing where the session connects.
	 */
	@Override
	public void packetSending(PacketSendingEvent event) {
		Packet packet = event.getPacket();
		String virtualHost = login.getConnection().getVirtualHost();
		if (virtualHost != null && packet instanceof HandshakePacket handshake)
			event.setPacket(handshake.withHostname(virtualHost));
	}

	@Override
	public void disconnected(DisconnectedEvent event) {
		DisconnectCause cause = authenticationRequired
				? DisconnectCause.AUTHENTICATION_REQUIRED
				: DisconnectCause.CONNECTION_LOST;
		listener.disconnected(event.getSession(), cause, Objects.requireNonNullElse(event.getReason(), ""));
	}
}
