package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.handshake.client.HandshakePacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPluginMessagePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
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
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.ClientBrand;
import me.whereareiam.anvil.player.library.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientProfile;

import java.util.Objects;

/**
 * Answers the packets a client of the MCProtocolLib releases from 1.16.5 until 1.17.1 must answer on its own and
 * reports login and disconnects with their reason flattened to plain text; the session's {@link FramedClientSession}
 * hands the packets themselves over and then calls this listener on the channel's thread, in the order the packets
 * arrived. The login reports the profile the server granted in its login success, which arrives before the game
 * starts. A vanilla client of these releases answers the game's start with its client information, which the release's
 * {@link ClientInformation} builds, followed by its {@link ClientBrand}. What the client answers between its ticks, such
 * as a rejected transaction or a swing of its own player, the worker kit's client answers.
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
	private final ClientInformation information;
	private final ComponentTranslator text;
	private volatile ClientProfile granted;
	private volatile boolean authenticationRequired;

	@Override
	public void packetReceived(PacketReceivedEvent event) {
		Session session = event.getSession();
		Packet packet = event.getPacket();
		if (packet instanceof EncryptionRequestPacket && login.getCredentials() == null) authenticationRequired = true;
		if (packet instanceof LoginSuccessPacket success) {
			granted = ClientProfile.builder()
					.name(success.getProfile().getName())
					.uniqueId(success.getProfile().getId())
					.build();
			listener.granted(session, granted);
		}
		if (packet instanceof ServerJoinGamePacket) {
			session.send(information.packet(login.getSettings()));
			session.send(new ClientPluginMessagePacket(ClientBrand.CHANNEL, ClientBrand.payload()));
			listener.loggedIn(session,
					Objects.requireNonNull(granted, "The server started the game before it granted a profile"));
		}
		if (packet instanceof ServerDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, text.translate(disconnect.getReason()).plain());
		if (packet instanceof LoginDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, text.translate(disconnect.getReason()).plain());
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
