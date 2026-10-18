package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.protocol.java.api.type.PlayerCommandAction;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ValueTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
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
 * Player movement of the MCProtocolLib releases from 1.21.11 on, whose movement packets also carry the horizontal
 * collision flag, whose client tells the keys it holds and the end of each tick and sneaks only through its keys, and
 * the server's placement of the player, which also sets its velocity.
 */
public final class PlayerPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundPlayerPosition.class,
				ServerboundClientTickEnd.class,
				ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class,
				ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerCommand.class,
				ServerboundPlayerInput.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundPlayerPositionPacket position)) return null;

		return ClientboundPlayerPosition.builder()
				.position(ValueTranslator.vec3d(position.getPosition()))
				.deltaMovement(ValueTranslator.vec3d(position.getDeltaMovement()))
				.yaw(position.getYRot())
				.pitch(position.getXRot())
				.relatives(relatives(position.getRelatives()))
				.build();
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return switch (packet) {
			case ServerboundMovePlayerPosRot move -> new ServerboundMovePlayerPosRotPacket(move.isOnGround(),
					move.isHorizontalCollision(), move.getPosition().getX(), move.getPosition().getY(),
					move.getPosition().getZ(), move.getYaw(), move.getPitch());
			case ServerboundMovePlayerPos move -> new ServerboundMovePlayerPosPacket(move.isOnGround(),
					move.isHorizontalCollision(), move.getPosition().getX(), move.getPosition().getY(),
					move.getPosition().getZ());
			case ServerboundMovePlayerRot turn -> new ServerboundMovePlayerRotPacket(turn.isOnGround(),
					turn.isHorizontalCollision(), turn.getYaw(), turn.getPitch());
			case ServerboundMovePlayerStatusOnly status -> new ServerboundMovePlayerStatusOnlyPacket(status.isOnGround(),
					status.isHorizontalCollision());
			case ServerboundPlayerInput input -> new ServerboundPlayerInputPacket(input.isForward(), input.isBackward(),
					input.isLeft(), input.isRight(), input.isJump(), input.isShift(), input.isSprint());
			case ServerboundClientTickEnd ignored -> ServerboundClientTickEndPacket.INSTANCE;
			case ServerboundPlayerCommand command -> new ServerboundPlayerCommandPacket(command.getEntityId(),
					state(command.getAction()));
			default -> null;
		};
	}

	/**
	 * Names the player state of a command; the release tells sneaking only through the player's keys.
	 */
	private static PlayerState state(PlayerCommandAction action) {
		return switch (action) {
			case START_SPRINTING -> PlayerState.START_SPRINTING;
			case STOP_SPRINTING -> PlayerState.STOP_SPRINTING;
			case PRESS_SHIFT_KEY, RELEASE_SHIFT_KEY -> throw new ProtocolUnavailableException("the player's release tells "
					+ "sneaking only through " + ServerboundPlayerInput.class.getName() + ", not through the player "
					+ "command " + action);
		};
	}

	/**
	 * Names the coordinates, angles and velocity components a teleport adds to the current ones.
	 */
	static Set<Relative> relatives(List<PositionElement> elements) {
		Set<Relative> relatives = EnumSet.noneOf(Relative.class);
		for (PositionElement element : elements) relatives.add(switch (element) {
			case X -> Relative.X;
			case Y -> Relative.Y;
			case Z -> Relative.Z;
			case Y_ROT -> Relative.YAW;
			case X_ROT -> Relative.PITCH;
			case DELTA_X -> Relative.DELTA_X;
			case DELTA_Y -> Relative.DELTA_Y;
			case DELTA_Z -> Relative.DELTA_Z;
			case ROTATE_DELTA -> Relative.ROTATE_DELTA;
		});
		return Collections.unmodifiableSet(relatives);
	}
}
