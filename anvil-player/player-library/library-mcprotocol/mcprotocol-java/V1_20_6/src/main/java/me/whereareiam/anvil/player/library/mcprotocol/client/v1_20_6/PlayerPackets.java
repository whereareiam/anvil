package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

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
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The player of the MCProtocolLib 1.20.6 and 1.21.1 releases: its movement, whose packets carry no horizontal
 * collision flag, its player command, which still tells sneaking as well as sprinting, the abilities the server grants
 * it, the flight it starts or stops on its own, whose abilities packet carries only whether it flies, and the server's
 * placement of it. A placement names relative coordinates and angles and carries no velocity: the
 * client keeps its velocity along each relative coordinate and stops it along each absolute one, which the translation
 * reports as a zero velocity that is relative along each relative coordinate.
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
			case ServerboundMovePlayerPosRot move -> new ServerboundMovePlayerPosRotPacket(move.onGround(),
					move.position().x(), move.position().y(), move.position().z(), move.yaw(),
					move.pitch());
			case ServerboundMovePlayerPos move -> new ServerboundMovePlayerPosPacket(move.onGround(),
					move.position().x(), move.position().y(), move.position().z());
			case ServerboundMovePlayerRot turn ->
					new ServerboundMovePlayerRotPacket(turn.onGround(), turn.yaw(), turn.pitch());
			case ServerboundMovePlayerStatusOnly status -> new ServerboundMovePlayerStatusOnlyPacket(status.onGround());
			case ServerboundPlayerCommand command ->
					new ServerboundPlayerCommandPacket(command.entityId().value(), state(command.action()));
			case ServerboundPlayerAbilities abilities -> new ServerboundPlayerAbilitiesPacket(abilities.flying());
			default -> null;
		};
	}

	/**
	 * Translates a player command's action to the library's player state, which still tells sneaking.
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
	 * Names the coordinates and angles a placement adds to the current ones, and the velocity the client keeps along
	 * each relative coordinate.
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
