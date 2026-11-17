package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerState;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerMovementPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerStatePacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundAcceptTeleportation;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
import me.whereareiam.anvil.protocol.java.api.type.player.PlayerCommandAction;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The player of the MCProtocolLib releases from 1.16.5 until 1.17.1: its movement, whose packets carry no horizontal
 * collision flag, the player command, which still tells sneaking as well as sprinting, what the server tells the
 * player about itself, and the flight the player starts or stops on its own, whose abilities packet carries only
 * whether it flies, as from Minecraft 1.16. The server's placement of the player names relative coordinates and angles
 * and carries no velocity: the client keeps its velocity along each relative coordinate and stops it along each
 * absolute one, so the translation reports a zero velocity that is relative along each relative coordinate. These
 * releases have neither the player input nor the end of a client tick.
 */
public final class PlayerPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundPlayerAbilities.class,
				ClientboundPlayerPosition.class,
				ServerboundAcceptTeleportation.class,
				ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class,
				ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerAbilities.class,
				ServerboundPlayerCommand.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ServerPlayerPositionRotationPacket position -> ClientboundPlayerPosition.builder()
					.id(position.getTeleportId())
					.position(new Vec3d(position.getX(), position.getY(), position.getZ()))
					.yaw(position.getYaw())
					.pitch(position.getPitch())
					.relatives(relatives(position.getRelative()))
					.build();
			case ServerPlayerAbilitiesPacket abilities -> ClientboundPlayerAbilities.builder()
					.invulnerable(abilities.isInvincible())
					.flying(abilities.isFlying())
					.mayFly(abilities.isCanFly())
					.instabuild(abilities.isCreative())
					.flyingSpeed(abilities.getFlySpeed())
					.walkingSpeed(abilities.getWalkSpeed())
					.build();
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return switch (packet) {
			case ServerboundAcceptTeleportation accept -> new ClientTeleportConfirmPacket(accept.getId());
			case ServerboundMovePlayerPosRot move -> new ClientPlayerPositionRotationPacket(move.isOnGround(),
					move.getPosition().getX(), move.getPosition().getY(), move.getPosition().getZ(), move.getYaw(),
					move.getPitch());
			case ServerboundMovePlayerPos move -> new ClientPlayerPositionPacket(move.isOnGround(),
					move.getPosition().getX(), move.getPosition().getY(), move.getPosition().getZ());
			case ServerboundMovePlayerRot turn ->
					new ClientPlayerRotationPacket(turn.isOnGround(), turn.getYaw(), turn.getPitch());
			case ServerboundMovePlayerStatusOnly status -> new ClientPlayerMovementPacket(status.isOnGround());
			case ServerboundPlayerCommand command ->
					new ClientPlayerStatePacket(command.getEntityId().getValue(), state(command.getAction()));
			case ServerboundPlayerAbilities abilities -> new ClientPlayerAbilitiesPacket(abilities.isFlying());
			default -> null;
		};
	}

	/**
	 * Names the player state of a command; the player command of these releases carries every action.
	 */
	private static PlayerState state(PlayerCommandAction action) {
		return switch (action) {
			case PRESS_SHIFT_KEY -> PlayerState.START_SNEAKING;
			case RELEASE_SHIFT_KEY -> PlayerState.STOP_SNEAKING;
			case START_SPRINTING -> PlayerState.START_SPRINTING;
			case STOP_SPRINTING -> PlayerState.STOP_SPRINTING;
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
