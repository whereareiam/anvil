package me.whereareiam.anvil.player.library.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.UnexpectedEncryptionException;
import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundCustomPayloadPacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
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

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * Answers the packets a client of the MCProtocolLib releases from 1.18.2 until 1.19.4 must answer on its own and
 * reports login and disconnects with their reason flattened to plain text; the session's {@link FramedClientSession}
 * hands the packets themselves over and then calls this listener on the channel's thread, in the order the packets
 * arrived. What the client answers between its ticks, such as a ping or a swing of its own player, the worker kit's
 * client answers. The login reports the profile the server granted in its login success, which arrives before the game
 * starts. A vanilla client of these releases answers the game's start with its client information followed by its
 * {@link ClientBrand}. Each release's adapter reads the reason of a closed connection, which packetlib turned from a
 * string into a component.
 */
@RequiredArgsConstructor
final class ClientPacketListener extends SessionAdapter {
	private final ClientLogin login;
	private final ClientListener<? super Session> listener;
	private final Function<DisconnectedEvent, String> reasons;
	private final ComponentTranslator text;
	private volatile ClientProfile granted;

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (packet instanceof ClientboundGameProfilePacket success) {
			granted = ClientProfile.builder().name(success.getProfile().getName()).uniqueId(success.getProfile().getId()).build();
			listener.granted(session, granted);
		}
		if (packet instanceof ClientboundLoginPacket) {
			session.send(new ServerboundClientInformationPacket(login.getSettings().getLocale(), login.getSettings().getViewDistance(), ChatVisibility.FULL,
					true, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, false, true));
			session.send(new ServerboundCustomPayloadPacket(ClientBrand.CHANNEL, ClientBrand.payload()));
			listener.loggedIn(session, Objects.requireNonNull(granted, "The server started the game before it granted a profile"));
		}
		if (packet instanceof ClientboundDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, text.translate(disconnect.getReason()).plain());
		if (packet instanceof ClientboundLoginDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, text.translate(disconnect.getReason()).plain());
	}

	/**
	 * MCProtocolLib announces the host it connects to; packetlib lets a listener replace a packet before
	 * it is written, which announces the virtual host without changing where the session connects.
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
		listener.disconnected(event.getSession(), cause(event), reasons.apply(event));
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
