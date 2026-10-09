package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;

import java.util.Arrays;

/**
 * Answers the packets an MCProtocolLib 1.21.11 client must answer on its own and reports login, teleports and
 * disconnects with their reason flattened to plain text.
 */
@RequiredArgsConstructor
final class ClientPacketListener extends SessionAdapter {
	private final ClientSession session;
	private final ClientLogin login;
	private final ClientListener<? super ClientSession> listener;

	@Override
	public void packetReceived(Session ignored, Packet packet) {
		if (packet instanceof ClientboundLoginPacket) {
			session.send(new ServerboundClientInformationPacket(login.getLocale(), login.getViewDistance(), ChatVisibility.FULL,
					true, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, false, true, ParticleStatus.ALL));
			listener.loggedIn(session);
		}
		if (packet instanceof ClientboundPlayerPositionPacket position) {
			listener.teleported(session, position.getYRot(), position.getXRot());
			session.send(new ServerboundAcceptTeleportationPacket(position.getId()));
			session.send(ServerboundPlayerLoadedPacket.INSTANCE);
		}
		if (packet instanceof ClientboundDisconnectPacket disconnect) listener.disconnected(session, plainText(disconnect.getReason()));
		if (packet instanceof ClientboundLoginDisconnectPacket disconnect)
			listener.disconnected(session, plainText(disconnect.getReason()));
	}

	@Override
	public void disconnected(DisconnectedEvent event) {
		listener.disconnected(session, event.getReason() == null ? "" : plainText(event.getReason()));
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
