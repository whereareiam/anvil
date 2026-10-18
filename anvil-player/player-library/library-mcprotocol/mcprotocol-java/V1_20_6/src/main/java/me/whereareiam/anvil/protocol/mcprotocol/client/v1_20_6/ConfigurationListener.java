package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientBrand;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Starts the configuration of the MCProtocolLib releases from 1.20.5 until 1.21.1 as a vanilla client does and answers
 * every keep-alive. Once the login is acknowledged, a vanilla client sends its {@link ClientBrand} and then its client
 * information, and it sends neither again: not when the server starts another configuration, nor when the game
 * starts.
 *
 * <p>The session adds this listener after MCProtocolLib's own, which acknowledges the login and switches the session
 * to the configuration phase once the acknowledgement is written, so the brand and the information follow the
 * acknowledgement in that phase. MCProtocolLib 1.20.6 answers keep-alives only while playing, but a server may expect
 * the answer during the configuration as well, as the anti-bot plugin Sonar does before it sends the registries; the
 * sessions turn MCProtocolLib's answers off with {@link #disableLibraryKeepAlives(Session)} and this listener answers
 * keep-alives in both phases.</p>
 */
@RequiredArgsConstructor
public final class ConfigurationListener extends SessionAdapter {
	private static final Key BRAND = Key.key(ClientBrand.CHANNEL);
	private static final boolean CHAT_COLORS = true;
	private static final boolean TEXT_FILTERING = false;
	private static final boolean SERVER_LISTING = true;

	/**
	 * The settings the client information reports.
	 */
	private final ClientSettings settings;

	/**
	 * Turns off MCProtocolLib's own keep-alive answers on a session whose keep-alives this listener answers.
	 *
	 * @param session a session of the release
	 */
	public static void disableLibraryKeepAlives(@NotNull Session session) {
		session.setFlag(MinecraftConstants.AUTOMATIC_KEEP_ALIVE_MANAGEMENT, false);
	}

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (packet instanceof ClientboundKeepAlivePacket keepAlive)
			session.send(new ServerboundKeepAlivePacket(keepAlive.getPingId()));
	}

	@Override
	public void packetSent(Session session, Packet packet) {
		if (!(packet instanceof ServerboundLoginAcknowledgedPacket)) return;

		session.send(new ServerboundCustomPayloadPacket(BRAND, ClientBrand.payload()));
		session.send(new ServerboundClientInformationPacket(settings.getLocale(), settings.getViewDistance(),
				ChatVisibility.FULL, CHAT_COLORS, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND,
				TEXT_FILTERING, SERVER_LISTING));
	}
}
