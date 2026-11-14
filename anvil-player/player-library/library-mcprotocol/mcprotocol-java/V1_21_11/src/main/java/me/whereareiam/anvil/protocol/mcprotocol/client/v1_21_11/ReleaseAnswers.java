package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Animation;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAnimatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;

/**
 * What a client of the MCProtocolLib releases from 1.21.11 until 26.2 answers on its own in packets that Minecraft
 * 26.3 changed. It reports each teleport, accepts it by its ID and announces that the player loaded. When the server
 * swings an arm of the player's own entity, it swings that arm back, as the vanilla client's own swing does
 * ({@code LocalPlayer.swing}), which tells the server. {@link NetworkClientSessions} adds it to every session before
 * the {@code ClientPacketListener}, so a teleport is reported before its packet is delivered.
 */
@RequiredArgsConstructor
public final class ReleaseAnswers extends SessionAdapter {
	private final ClientSession session;
	private final ClientListener<? super ClientSession> listener;
	/**
	 * The protocol ID of the player's own entity, which the game's start names before the server animates it.
	 */
	private volatile int entityId;

	@Override
	public void packetReceived(Session ignored, Packet packet) {
		switch (packet) {
			case ClientboundLoginPacket login -> entityId = login.getEntityId();
			case ClientboundPlayerPositionPacket position -> {
				listener.teleported(session, position.getYRot(), position.getXRot());
				session.send(new ServerboundAcceptTeleportationPacket(position.getId()));
				session.send(ServerboundPlayerLoadedPacket.INSTANCE);
			}
			case ClientboundAnimatePacket animation when animation.getEntityId() == entityId ->
					swingBack(animation.getAnimation());
			default -> {
			}
		}
	}

	private void swingBack(Animation animation) {
		if (animation == Animation.SWING_ARM) session.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
		if (animation == Animation.SWING_OFFHAND) session.send(new ServerboundSwingPacket(Hand.OFF_HAND));
	}
}
