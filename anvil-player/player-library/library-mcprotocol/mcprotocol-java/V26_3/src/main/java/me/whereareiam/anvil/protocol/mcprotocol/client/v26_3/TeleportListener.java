package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Answers the teleports of the MCProtocolLib 26.3 release, which accepts a teleport with the position and view it
 * results in; the server treats the acceptance as the player's first movement from the teleport's target. The
 * listener therefore follows the position and view the client last sent or was teleported to, resolves each
 * teleport's relative coordinates and angles against them as the vanilla client does, and accepts the teleport with
 * the result before it announces that the player loaded. The teleport is reported as the server sent it.
 */
@RequiredArgsConstructor
final class TeleportListener extends SessionAdapter {
	private final ClientSession session;
	private final ClientListener<? super ClientSession> listener;
	private final AtomicReference<Placement> placement = new AtomicReference<>(new Placement(0, 0, 0, 0, 0));

	@Override
	public void packetReceived(Session ignored, Packet packet) {
		if (!(packet instanceof ClientboundPlayerPositionPacket position)) return;

		Placement target = placement.updateAndGet(current -> current.teleported(position));
		listener.teleported(session, position.getYRot(), position.getXRot());
		session.send(new ServerboundAcceptTeleportationPacket(position.getId(), target.x(), target.y(), target.z(),
				target.yaw(), target.pitch()));
		session.send(ServerboundPlayerLoadedPacket.INSTANCE);
	}

	@Override
	public void packetSent(Session ignored, Packet packet) {
		switch (packet) {
			case ServerboundMovePlayerPosRotPacket move ->
					placement.set(new Placement(move.getX(), move.getY(), move.getZ(), move.getYaw(), move.getPitch()));
			case ServerboundMovePlayerPosPacket move -> placement.updateAndGet(current ->
					new Placement(move.getX(), move.getY(), move.getZ(), current.yaw(), current.pitch()));
			case ServerboundMovePlayerRotPacket turn -> placement.updateAndGet(current ->
					new Placement(current.x(), current.y(), current.z(), turn.getYaw(), turn.getPitch()));
			default -> {
			}
		}
	}

	/**
	 * Position and view of the player, absolute in the world.
	 */
	private record Placement(double x, double y, double z, float yaw, float pitch) {
		private Placement teleported(ClientboundPlayerPositionPacket teleport) {
			List<PositionElement> relative = teleport.getRelatives();
			return new Placement(
					relative.contains(PositionElement.X) ? x + teleport.getPosition().getX() : teleport.getPosition().getX(),
					relative.contains(PositionElement.Y) ? y + teleport.getPosition().getY() : teleport.getPosition().getY(),
					relative.contains(PositionElement.Z) ? z + teleport.getPosition().getZ() : teleport.getPosition().getZ(),
					relative.contains(PositionElement.Y_ROT) ? yaw + teleport.getYRot() : teleport.getYRot(),
					relative.contains(PositionElement.X_ROT) ? pitch + teleport.getXRot() : teleport.getXRot()
			);
		}
	}
}
