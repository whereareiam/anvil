package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ValueTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
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
 * The player of the MCProtocolLib releases from 1.21.11 on: its movement packets carry the horizontal collision flag,
 * its client tells the keys it holds and the end of each tick and sneaks only through those keys, the server's
 * placement of the player also sets its velocity, the server grants the player's abilities, and the player tells the
 * flight it starts or stops on its own, whose abilities packet carries only whether it flies.
 */
public final class PlayerPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundPlayerAbilities.class,
				ClientboundPlayerPosition.class,
				ServerboundClientTickEnd.class,
				ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class,
				ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerAbilities.class,
				ServerboundPlayerCommand.class,
				ServerboundPlayerInput.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundPlayerPositionPacket position -> ClientboundPlayerPosition.builder()
					.position(ValueTranslator.vec3d(position.getPosition()))
					.yaw(position.getYRot())
					.pitch(position.getXRot())
					.deltaMovement(ValueTranslator.vec3d(position.getDeltaMovement()))
					.relatives(relatives(position.getRelatives()))
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
			case ServerboundMovePlayerPosRot move -> {
				Vec3d position = move.getPosition();
				yield new ServerboundMovePlayerPosRotPacket(move.isOnGround(), move.isHorizontalCollision(),
						position.getX(), position.getY(), position.getZ(), move.getYaw(), move.getPitch());
			}
			case ServerboundMovePlayerPos move -> {
				Vec3d position = move.getPosition();
				yield new ServerboundMovePlayerPosPacket(move.isOnGround(), move.isHorizontalCollision(),
						position.getX(), position.getY(), position.getZ());
			}
			case ServerboundMovePlayerRot turn -> new ServerboundMovePlayerRotPacket(turn.isOnGround(),
					turn.isHorizontalCollision(), turn.getYaw(), turn.getPitch());
			case ServerboundMovePlayerStatusOnly status ->
					new ServerboundMovePlayerStatusOnlyPacket(status.isOnGround(), status.isHorizontalCollision());
			case ServerboundPlayerInput input -> new ServerboundPlayerInputPacket(input.isForward(), input.isBackward(),
					input.isLeft(), input.isRight(), input.isJump(), input.isShift(), input.isSprint());
			case ServerboundClientTickEnd ignored -> ServerboundClientTickEndPacket.INSTANCE;
			case ServerboundPlayerAbilities abilities -> new ServerboundPlayerAbilitiesPacket(abilities.isFlying());
			case ServerboundPlayerCommand command ->
					new ServerboundPlayerCommandPacket(command.getEntityId().getValue(), state(command.getAction()));
			default -> null;
		};
	}

	/**
	 * Names the coordinates, angles and velocity components a placement adds to the current ones.
	 *
	 * @param elements the release's relative elements
	 * @return the protocol's relatives
	 */
	static @NotNull Set<Relative> relatives(@NotNull List<PositionElement> elements) {
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

	/**
	 * Names the player state of a command; since Minecraft 1.21.6 the release's player command carries no sneaking,
	 * which the client tells only through the keys it holds.
	 */
	private static PlayerState state(PlayerCommandAction action) {
		return switch (action) {
			case START_SPRINTING -> PlayerState.START_SPRINTING;
			case STOP_SPRINTING -> PlayerState.STOP_SPRINTING;
			case PRESS_SHIFT_KEY, RELEASE_SHIFT_KEY -> throw new ProtocolUnavailableException("the player's release "
					+ "carries no " + action + " player command; it tells sneaking only through "
					+ ServerboundPlayerInput.class.getSimpleName());
		};
	}
}
