package me.whereareiam.anvil.capability.movement.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Movement packets of MCProtocolLib releases from Minecraft 1.21.11, whose position packet also carries the
 * horizontal collision flag.
 */
public final class McProtocolMovementPackets implements MovementPackets<Session> {
	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void move(@NotNull Session session, @NotNull Position position) {
		session.send(new ServerboundMovePlayerPosRotPacket(position.isOnGround(), false,
				position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch()));
	}
}
