package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundAcceptTeleportation;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerLoaded;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ValueTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The player of the MCProtocolLib releases from 1.21.11 on: its movement packets carry the horizontal collision flag,
 * its client accepts a teleport by its ID, tells the keys it holds, the end of each tick and that it loaded its level,
 * and sneaks only through those keys, the server's
 * placement of the player also sets its velocity, the server grants the player's abilities, and the player tells the
 * flight it starts or stops on its own, whose abilities packet carries only whether it flies.
 */
public final class PlayerPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundPlayerAbilities.class,
				ClientboundPlayerPosition.class,
				ServerboundAcceptTeleportation.class,
				ServerboundClientTickEnd.class,
				ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class,
				ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerAbilities.class,
				ServerboundPlayerCommand.class,
				ServerboundPlayerInput.class,
				ServerboundPlayerLoaded.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundPlayerPositionPacket position -> ClientboundPlayerPosition.builder()
					.id(position.getId())
					.position(ValueTranslator.vec3d(position.getPosition()))
					.yaw(position.getYRot())
					.pitch(position.getXRot())
					.deltaMovement(ValueTranslator.vec3d(position.getDeltaMovement()))
					.relatives(RelativeElements.translate(position.getRelatives()))
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
			case ServerboundAcceptTeleportation accept -> new ServerboundAcceptTeleportationPacket(accept.getId());
			case ServerboundClientTickEnd ignored -> ServerboundClientTickEndPacket.INSTANCE;
			case ServerboundPlayerLoaded ignored -> ServerboundPlayerLoadedPacket.INSTANCE;
			case ServerboundPlayerAbilities abilities -> new ServerboundPlayerAbilitiesPacket(abilities.isFlying());
			case ServerboundPlayerCommand command ->
					new ServerboundPlayerCommandPacket(command.getEntityId().getValue(), state(command.getAction()));
			default -> null;
		};
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
