package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.jetbrains.annotations.NotNull;

/**
 * Answers every keep-alive of a session of the MCProtocolLib 1.20.6 release, in the configuration as well as in the
 * game, as a vanilla client does. The release answers keep-alives only while playing, but a server may expect the
 * answer during the configuration too, as the anti-bot plugin Sonar does before it sends the registries.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class KeepAliveAnswers extends SessionAdapter {
	/**
	 * Turns off MCProtocolLib's own keep-alive answers on a session and answers its keep-alives instead.
	 *
	 * @param session a session of the release
	 */
	static void install(@NotNull Session session) {
		session.setFlag(MinecraftConstants.AUTOMATIC_KEEP_ALIVE_MANAGEMENT, false);
		session.addListener(new KeepAliveAnswers());
	}

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (packet instanceof ClientboundKeepAlivePacket keepAlive)
			session.send(new ServerboundKeepAlivePacket(keepAlive.getPingId()));
	}
}
