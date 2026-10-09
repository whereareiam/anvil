package me.whereareiam.anvil.capability.movement.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import com.github.steveice10.packetlib.Session;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import org.jetbrains.annotations.NotNull;

/**
 * Movement packets of MCProtocolLib releases from Minecraft 1.18.2, the first ones with serverbound packet
 * names.
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
