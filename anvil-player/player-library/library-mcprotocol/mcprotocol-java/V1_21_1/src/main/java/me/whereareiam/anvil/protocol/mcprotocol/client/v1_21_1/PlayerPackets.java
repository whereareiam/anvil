package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Player movement of the MCProtocolLib 1.21.1 release, whose position packet carries no horizontal collision
 * flag, and the server's placement of the player, which names relative coordinates and angles.
 */
final class PlayerPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundPlayerPosition.class, ServerboundMovePlayerPosRot.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundPlayerPositionPacket position)) return null;

		return ClientboundPlayerPosition.builder()
				.position(new Vec3d(position.getX(), position.getY(), position.getZ()))
				.yaw(position.getYaw())
				.pitch(position.getPitch())
				.relatives(relatives(position.getRelative()))
				.build();
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundMovePlayerPosRot move)) return null;

		Vec3d position = move.getPosition();
		return new ServerboundMovePlayerPosRotPacket(move.isOnGround(),
				position.getX(), position.getY(), position.getZ(), move.getYaw(), move.getPitch());
	}

	/**
	 * Names the coordinates and angles a teleport adds to the current ones.
	 */
	private static Set<Relative> relatives(List<PositionElement> elements) {
		Set<Relative> relatives = EnumSet.noneOf(Relative.class);
		for (PositionElement element : elements) relatives.add(switch (element) {
			case X -> Relative.X;
			case Y -> Relative.Y;
			case Z -> Relative.Z;
			case YAW -> Relative.YAW;
			case PITCH -> Relative.PITCH;
		});
		return Collections.unmodifiableSet(relatives);
	}
}
