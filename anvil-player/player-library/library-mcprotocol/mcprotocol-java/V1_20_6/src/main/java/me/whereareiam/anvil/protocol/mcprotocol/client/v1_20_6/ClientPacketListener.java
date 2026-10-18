package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureText;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.UnexpectedEncryptionException;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * Answers the packets a client of the MCProtocolLib releases from 1.20.5 until 1.21.1 must answer on its own and
 * reports login, teleports and disconnects with their reason flattened to plain text. The login reports the profile the
 * server granted in its login success, which arrives before the game starts; each release's sessions read that profile,
 * since MCProtocolLib 1.21 replaced mcauthlib's profile with its own.
 */
@RequiredArgsConstructor
public final class ClientPacketListener extends SessionAdapter {
	/**
	 * The login the listener answers for.
	 */
	private final ClientLogin login;
	/**
	 * The receiver of the session's state and packets.
	 */
	private final ClientListener<? super Session> listener;
	/**
	 * The table the session decodes through.
	 */
	private final PacketTable<Packet> packets;
	/**
	 * Reads the profile a login success grants, in the release's own profile type.
	 */
	private final Function<ClientboundGameProfilePacket, ClientProfile> profiles;
	private volatile ClientProfile granted;

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (packet instanceof ClientboundGameProfilePacket success) granted = profiles.apply(success);
		if (packet instanceof ClientboundLoginPacket) {
			session.send(new ServerboundClientInformationPacket(login.getSettings().getLocale(), login.getSettings().getViewDistance(), ChatVisibility.FULL,
					true, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, false, true));
			listener.loggedIn(session, Objects.requireNonNull(granted, "The server started the game before it granted a profile"));
		}
		if (packet instanceof ClientboundPlayerPositionPacket position) {
			listener.teleported(session, position.getYaw(), position.getPitch());
			session.send(new ServerboundAcceptTeleportationPacket(position.getTeleportId()));
		}
		// A vanilla client answers every ping; NeoForge waits for the answer to tell a vanilla client from a modded
		// one.
		if (packet instanceof ClientboundPingPacket ping) session.send(new ServerboundPongPacket(ping.getId()));
		if (packet instanceof ClientboundDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, AdventureText.plain(disconnect.getReason()));
		if (packet instanceof ClientboundLoginDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, AdventureText.plain(disconnect.getReason()));

		ClientboundPacket translated = packets.decode(packet);
		if (translated != null) listener.received(session, translated);
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
		String reason = event.getReason() == null ? "" : AdventureText.plain(event.getReason());
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
