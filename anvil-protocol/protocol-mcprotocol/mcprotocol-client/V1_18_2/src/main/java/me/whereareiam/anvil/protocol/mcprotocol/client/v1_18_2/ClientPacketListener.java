package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.UnexpectedEncryptionException;
import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.PacketSendingEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;

import java.util.Arrays;
import java.util.Objects;

/**
 * Answers the packets an MCProtocolLib 1.18.2 client must answer on its own and reports login, teleports and
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
		if (packet instanceof ClientboundDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, plainText(disconnect.getReason()));
		if (packet instanceof ClientboundLoginDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, plainText(disconnect.getReason()));
	}

	/**
	 * MCProtocolLib 1.18.2 announces the host it connects to; packetlib lets a listener replace a packet before
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
		listener.disconnected(event.getSession(), cause(event), Objects.requireNonNullElse(event.getReason(), ""));
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
			for (Component argument : value.args()) {
				text.append(' ');
				append(argument, text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}
}
