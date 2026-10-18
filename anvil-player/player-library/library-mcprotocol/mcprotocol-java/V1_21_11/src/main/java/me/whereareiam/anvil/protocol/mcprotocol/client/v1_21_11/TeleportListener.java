package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;

/**
 * Answers the teleports of the MCProtocolLib releases from 1.21.11 until 26.2: reports each teleport, accepts it by
 * its ID and announces that the player loaded. {@link NetworkClientSessions} adds it to every session before the
 * {@code ClientPacketListener}, so the teleport is reported before its packet is delivered.
 */
@RequiredArgsConstructor
public final class TeleportListener extends SessionAdapter {
	private final ClientSession session;
	private final ClientListener<? super ClientSession> listener;

	@Override
	public void packetReceived(Session ignored, Packet packet) {
		if (!(packet instanceof ClientboundPlayerPositionPacket position)) return;

		listener.teleported(session, position.getYRot(), position.getXRot());
		session.send(new ServerboundAcceptTeleportationPacket(position.getId()));
		session.send(ServerboundPlayerLoadedPacket.INSTANCE);
	}
}
