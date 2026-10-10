package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
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
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;

import java.util.Arrays;

/**
 * Answers the packets an MCProtocolLib 1.21.1 client must answer on its own and reports login, teleports and
 * disconnects with their reason flattened to plain text.
 */
@RequiredArgsConstructor
final class ClientPacketListener extends SessionAdapter {
	private final ClientLogin login;
	private final ClientListener<? super Session> listener;

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (packet instanceof ClientboundLoginPacket) {
			session.send(new ServerboundClientInformationPacket(login.getSettings().getLocale(), login.getSettings().getViewDistance(), ChatVisibility.FULL,
					true, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, false, true));
			listener.loggedIn(session);
		}
		if (packet instanceof ClientboundPlayerPositionPacket position) {
			listener.teleported(session, position.getYaw(), position.getPitch());
			session.send(new ServerboundAcceptTeleportationPacket(position.getTeleportId()));
		}
		// A vanilla client answers every ping; NeoForge waits for the answer to tell a vanilla client from a modded one.
		if (packet instanceof ClientboundPingPacket ping) session.send(new ServerboundPongPacket(ping.getId()));
		if (packet instanceof ClientboundDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, plainText(disconnect.getReason()));
		if (packet instanceof ClientboundLoginDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, plainText(disconnect.getReason()));
	}

	/**
	 * MCProtocolLib 1.21.1 announces the host it connects to; the network layer lets a listener replace a packet
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
		listener.disconnected(event.getSession(), cause(event), event.getReason() == null ? "" : plainText(event.getReason()));
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

	private static String plainText(Component component) {
		StringBuilder text = new StringBuilder();
		append(component, text);
		return text.toString();
	}

	private static void append(Component component, StringBuilder text) {
		if (component instanceof TextComponent value) text.append(value.content());
		if (component instanceof TranslatableComponent value) {
			text.append(value.key());
			for (TranslationArgument argument : value.arguments()) {
				text.append(' ');
				append(argument.asComponent(), text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}
}
