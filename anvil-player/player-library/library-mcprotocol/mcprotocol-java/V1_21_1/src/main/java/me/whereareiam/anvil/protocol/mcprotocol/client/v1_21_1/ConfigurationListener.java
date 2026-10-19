package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientBrand;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;

import java.util.Arrays;

/**
 * Starts the configuration of the MCProtocolLib 1.21.1 release as a vanilla client does. Once the login is
 * acknowledged, a vanilla client sends its {@link ClientBrand} and then its client information, and it sends neither
 * again: not when the server starts another configuration, nor when the game starts.
 *
 * <p>The session adds this listener after MCProtocolLib's own, which answers the login's success with the
 * acknowledgement and switches the session's outbound packets to the configuration phase right after writing it, so
 * the brand and the information that this listener sends for the same packet follow the acknowledgement in that phase.
 * They cannot wait until the acknowledgement is written, as on 1.20.6: MCProtocolLib 1.21 reports it written before
 * it switches the phase. MCProtocolLib answers keep-alives in both phases itself.</p>
 */
@RequiredArgsConstructor
final class ConfigurationListener extends SessionAdapter {
	private static final Key BRAND = Key.key(ClientBrand.CHANNEL);
	private static final boolean CHAT_COLORS = true;
	private static final boolean TEXT_FILTERING = false;
	private static final boolean SERVER_LISTING = true;

	/**
	 * The settings the client information reports.
	 */
	private final ClientSettings settings;

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (!(packet instanceof ClientboundGameProfilePacket)) return;

		session.send(new ServerboundCustomPayloadPacket(BRAND, ClientBrand.payload()));
		session.send(new ServerboundClientInformationPacket(settings.getLocale(), settings.getViewDistance(),
				ChatVisibility.FULL, CHAT_COLORS, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND,
				TEXT_FILTERING, SERVER_LISTING));
	}
}
