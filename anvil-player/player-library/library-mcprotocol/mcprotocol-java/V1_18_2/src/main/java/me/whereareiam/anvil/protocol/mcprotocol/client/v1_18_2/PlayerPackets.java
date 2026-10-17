package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.player.Position;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Player movement of Minecraft 1.18.2, whose position packet carries no horizontal collision flag.
 */
final class PlayerPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ServerboundMovePlayerPosRot.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundMovePlayerPosRot move)) return null;

		Position position = move.getPosition();
		return new ServerboundMovePlayerPosRotPacket(move.isOnGround(),
				position.getX(), position.getY(), position.getZ(), move.getYaw(), move.getPitch());
	}
}
