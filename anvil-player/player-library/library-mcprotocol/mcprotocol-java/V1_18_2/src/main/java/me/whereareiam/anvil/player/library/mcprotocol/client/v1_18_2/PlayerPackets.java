package me.whereareiam.anvil.player.library.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerState;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The player of the MCProtocolLib releases from 1.18.2 until 1.19.4: its movement, whose packets carry no horizontal
 * collision flag, its player command, which still tells sneaking besides sprinting, the abilities its game mode gives
 * it, the flight it starts or stops on its own, whose abilities packet carries only whether it flies, and the server's
 * placement of the player. A placement names relative coordinates and angles and carries no
 * velocity: the client keeps its velocity along each relative coordinate and stops it along each absolute one, which
 * the translation reports as a zero velocity that is relative along each relative coordinate.
 */
public final class PlayerPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundPlayerAbilities.class,
				ClientboundPlayerPosition.class,
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
			case ClientboundPlayerPositionPacket position -> ClientboundPlayerPosition.builder()
					.position(new Vec3d(position.getX(), position.getY(), position.getZ()))
					.yaw(position.getYaw())
					.pitch(position.getPitch())
					.relatives(relatives(position.getRelative()))
					.build();
			case ClientboundPlayerAbilitiesPacket abilities -> ClientboundPlayerAbilities.builder()
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
			case ServerboundMovePlayerPosRot move -> new ServerboundMovePlayerPosRotPacket(move.isOnGround(),
					move.getPosition().getX(), move.getPosition().getY(), move.getPosition().getZ(), move.getYaw(),
					move.getPitch());
			case ServerboundMovePlayerPos move -> new ServerboundMovePlayerPosPacket(move.isOnGround(),
					move.getPosition().getX(), move.getPosition().getY(), move.getPosition().getZ());
			case ServerboundMovePlayerRot turn ->
					new ServerboundMovePlayerRotPacket(turn.isOnGround(), turn.getYaw(), turn.getPitch());
			case ServerboundMovePlayerStatusOnly status ->
					new ServerboundMovePlayerStatusOnlyPacket(status.isOnGround());
			case ServerboundPlayerCommand command ->
					new ServerboundPlayerCommandPacket(command.getEntityId().getValue(), state(command.getAction()));
			case ServerboundPlayerAbilities abilities -> new ServerboundPlayerAbilitiesPacket(abilities.isFlying());
			default -> null;
		};
	}

	/**
	 * Translates a player command's action to the library's player state, which names sneaking and sprinting alike.
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
	 * Names the coordinates and angles a placement adds to the current ones, and the velocity it keeps along each
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
