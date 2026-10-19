package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Player movement of the MCProtocolLib 1.20.6 and 1.21.1 releases, whose movement packets carry no horizontal
 * collision flag and whose player command tells sprinting and sneaking, and the server's placement of the player,
 * which names relative coordinates and angles and carries no velocity: the client keeps its velocity along each
 * relative coordinate and stops it along each absolute one.
 */
public final class PlayerPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundPlayerPosition.class,
				ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class,
				ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerCommand.class
		);
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
		return switch (packet) {
			case ServerboundMovePlayerPosRot move -> new ServerboundMovePlayerPosRotPacket(move.isOnGround(),
					move.getPosition().getX(), move.getPosition().getY(), move.getPosition().getZ(), move.getYaw(),
					move.getPitch());
			case ServerboundMovePlayerPos move -> new ServerboundMovePlayerPosPacket(move.isOnGround(),
					move.getPosition().getX(), move.getPosition().getY(), move.getPosition().getZ());
			case ServerboundMovePlayerRot turn -> new ServerboundMovePlayerRotPacket(turn.isOnGround(), turn.getYaw(),
					turn.getPitch());
			case ServerboundMovePlayerStatusOnly status -> new ServerboundMovePlayerStatusOnlyPacket(status.isOnGround());
			case ServerboundPlayerCommand command -> new ServerboundPlayerCommandPacket(command.getEntityId(),
					switch (command.getAction()) {
						case PRESS_SHIFT_KEY -> PlayerState.START_SNEAKING;
						case RELEASE_SHIFT_KEY -> PlayerState.STOP_SNEAKING;
						case START_SPRINTING -> PlayerState.START_SPRINTING;
						case STOP_SPRINTING -> PlayerState.STOP_SPRINTING;
					});
			default -> null;
		};
	}

	/**
	 * Names the coordinates and angles a teleport adds to the current ones, and the velocity it keeps along each
	 * relative coordinate.
	 */
	private static Set<Relative> relatives(List<PositionElement> elements) {
		Set<Relative> relatives = EnumSet.noneOf(Relative.class);
		for (PositionElement element : elements) {
			switch (element) {
				case X -> relatives.addAll(List.of(Relative.X, Relative.DELTA_X));
				case Y -> relatives.addAll(List.of(Relative.Y, Relative.DELTA_Y));
				case Z -> relatives.addAll(List.of(Relative.Z, Relative.DELTA_Z));
				case YAW -> relatives.add(Relative.YAW);
				case PITCH -> relatives.add(Relative.PITCH);
			}
		}
		return Collections.unmodifiableSet(relatives);
	}
}
