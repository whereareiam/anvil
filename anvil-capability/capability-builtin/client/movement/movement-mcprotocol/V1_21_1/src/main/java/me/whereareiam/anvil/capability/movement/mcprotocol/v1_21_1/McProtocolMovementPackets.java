package me.whereareiam.anvil.capability.movement.mcprotocol.v1_21_1;

import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Movement packets of the MCProtocolLib release for Minecraft 1.21.1, the first one in the
 * {@code org.geysermc.mcprotocollib} packages.
 */
public final class McProtocolMovementPackets implements MovementPackets<Session> {
	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void move(@NotNull Session session, @NotNull Position position) {
		session.send(new ServerboundMovePlayerPosRotPacket(position.isOnGround(),
				position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch()));
	}
}
