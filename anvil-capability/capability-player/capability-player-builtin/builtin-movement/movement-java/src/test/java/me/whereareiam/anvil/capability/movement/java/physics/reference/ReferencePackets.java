package me.whereareiam.anvil.capability.movement.java.physics.reference;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ServerboundPong;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ServerboundContainerAck;
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
import org.jetbrains.annotations.NotNull;

/**
 * Writes the packets Anvil's client sends as the lines of a reference trace: the packet's name, then the fields the
 * release's own packet carries, each double and float exact by {@link Double#toHexString(double)} and
 * {@link Float#toHexString(float)}. A movement packet carries the horizontal collision from Minecraft 1.21.2, and the
 * acceptance of a teleport the position and view from 26.3. The answer to a ping is the pong from Minecraft 1.17 and,
 * before it, the acknowledgement of the rejected transaction that stands for the ping; the answer to a swing the server
 * animates of the player is the same swing, until 26.3.
 */
final class ReferencePackets {
	/**
	 * The first release whose acceptance of a teleport carries the position and view it results in.
	 */
	private static final MinecraftVersion ACCEPTED_PLACEMENT = MinecraftVersion.parse("26.3");

	private final boolean horizontalCollision;
	private final boolean acceptedPlacement;

	/**
	 * @param release the release's Minecraft version
	 */
	ReferencePackets(@NotNull MinecraftVersion release) {
		this.horizontalCollision = ClientBehaviour.REPORTED_HORIZONTAL_COLLISION.appliesTo(release);
		this.acceptedPlacement = release.isAtLeast(ACCEPTED_PLACEMENT);
	}

	/**
	 * Formats a packet the client sent.
	 *
	 * @throws IllegalArgumentException for a packet the traces do not write
	 */
	@NotNull String line(@NotNull ServerboundPacket packet) {
		return switch (packet) {
			case ServerboundAcceptTeleportation accept -> accept(accept);
			case ServerboundMovePlayerPosRot move -> name(move) + position(move.getPosition()) + rotation(move.getYaw(), move.getPitch())
					+ status(move.isOnGround(), move.isHorizontalCollision());
			case ServerboundMovePlayerPos move -> name(move) + position(move.getPosition()) + status(move.isOnGround(), move.isHorizontalCollision());
			case ServerboundMovePlayerRot move -> name(move) + rotation(move.getYaw(), move.getPitch()) + status(move.isOnGround(),
					move.isHorizontalCollision());
			case ServerboundMovePlayerStatusOnly move -> name(move) + status(move.isOnGround(), move.isHorizontalCollision());
			case ServerboundPlayerInput input -> name(input) + " forward=" + input.isForward() + " backward=" + input.isBackward()
					+ " left=" + input.isLeft() + " right=" + input.isRight() + " jump=" + input.isJump() + " shift="
					+ input.isShift() + " sprint=" + input.isSprint();
			case ServerboundPlayerCommand command -> name(command) + " entityId=" + command.getEntityId().getValue() + " action="
					+ command.getAction().name();
			case ServerboundPlayerAbilities abilities -> name(abilities) + " flying=" + abilities.isFlying();
			case ServerboundClientTickEnd end -> name(end);
			case ServerboundPlayerLoaded loaded -> name(loaded);
			case ServerboundPong pong -> name(pong) + " id=" + pong.getId();
			case ServerboundSwing swing -> name(swing) + " hand=" + swing.getHand();
			case ServerboundContainerAck ack -> name(ack) + " containerId=" + ack.getContainerId() + " uid=" + ack.getUid()
					+ " accepted=" + ack.isAccepted();
			default -> throw new IllegalArgumentException("The client sent " + packet + ", which the traces do not write");
		};
	}

	private @NotNull String accept(@NotNull ServerboundAcceptTeleportation accept) {
		String line = name(accept) + " id=" + accept.getId();
		return acceptedPlacement ? line + position(accept.getPosition()) + rotation(accept.getYaw(), accept.getPitch()) : line;
	}

	private @NotNull String status(boolean onGround, boolean collision) {
		return " onGround=" + onGround + (horizontalCollision ? " horizontalCollision=" + collision : "");
	}

	private static @NotNull String position(@NotNull Vec3d position) {
		return " x=" + Double.toHexString(position.getX()) + " y=" + Double.toHexString(position.getY()) + " z="
				+ Double.toHexString(position.getZ());
	}

	private static @NotNull String rotation(float yaw, float pitch) {
		return " yaw=" + Float.toHexString(yaw) + " pitch=" + Float.toHexString(pitch);
	}

	private static @NotNull String name(@NotNull ServerboundPacket packet) {
		return packet.getClass().getSimpleName();
	}
}
