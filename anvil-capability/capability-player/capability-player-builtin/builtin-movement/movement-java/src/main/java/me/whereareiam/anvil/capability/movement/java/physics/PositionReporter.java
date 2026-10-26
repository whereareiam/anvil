package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.protocol.java.api.type.PlayerCommandAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides the packets the vanilla client sends after each tick of its player, as {@code LocalPlayer.tick} and
 * {@code LocalPlayer.sendPosition} do: the held keys when they changed (from 1.21.2), sprinting and, before 1.21.6,
 * sneaking when they changed, and one movement packet: the position when it moved more than {@code 2.0E-4} or every
 * 20 ticks, the rotation when it changed, both together, or the ground state (and, from 1.21.2, the horizontal
 * collision) when only that changed.
 */
final class PositionReporter {
	private final @NotNull MovementRules rules;
	private final int entityId;

	private double xLast;
	private double yLast;
	private double zLast;
	private float yawLast;
	private float pitchLast;
	private int positionReminder;
	private boolean lastOnGround;
	private boolean lastHorizontalCollision;
	private boolean wasSprinting;
	private boolean wasSneaking;
	private @NotNull MovementInput lastSentInput = MovementInput.NONE;

	PositionReporter(@NotNull MovementRules rules, int entityId) {
		this.rules = rules;
		this.entityId = entityId;
	}

	/**
	 * Returns the packets for the end of a tick, in the order the client sends them, and remembers what they report.
	 */
	@NotNull List<ServerboundPacket> report(@NotNull PlayerSimulation player) {
		List<ServerboundPacket> packets = new ArrayList<>();
		if (rules.playerInputPackets() && !player.keys().equals(lastSentInput)) {
			MovementInput keys = player.keys();
			packets.add(ServerboundPlayerInput.builder()
					.forward(keys.isForward())
					.backward(keys.isBackward())
					.left(keys.isLeft())
					.right(keys.isRight())
					.jump(keys.isJump())
					.shift(keys.isSneak())
					.sprint(keys.isSprint())
					.build());
			lastSentInput = keys;
		}

		if (player.sprinting() != wasSprinting) {
			wasSprinting = player.sprinting();
			packets.add(new ServerboundPlayerCommand(entityId, wasSprinting
					? PlayerCommandAction.START_SPRINTING
					: PlayerCommandAction.STOP_SPRINTING));
		}
		if (rules.sneakCommands() && player.sneaking() != wasSneaking) {
			wasSneaking = player.sneaking();
			packets.add(new ServerboundPlayerCommand(entityId, wasSneaking
					? PlayerCommandAction.PRESS_SHIFT_KEY
					: PlayerCommandAction.RELEASE_SHIFT_KEY));
		}

		movement(player, packets);
		return packets;
	}

	private void movement(@NotNull PlayerSimulation player, @NotNull List<ServerboundPacket> packets) {
		double dx = player.x() - xLast;
		double dy = player.y() - yLast;
		double dz = player.z() - zLast;
		double dYaw = player.yaw() - yawLast;
		double dPitch = player.pitch() - pitchLast;
		positionReminder++;
		boolean moved = dx * dx + dy * dy + dz * dz > 2.0E-4 * 2.0E-4 || positionReminder >= 20;
		boolean turned = dYaw != 0.0 || dPitch != 0.0;
		boolean collision = rules.playerInputPackets() && player.horizontalCollision();
		Vec3d position = new Vec3d(player.x(), player.y(), player.z());
		if (moved && turned) {
			packets.add(ServerboundMovePlayerPosRot.builder()
					.position(position)
					.yaw(player.yaw())
					.pitch(player.pitch())
					.onGround(player.onGround())
					.horizontalCollision(collision)
					.build());
		} else if (moved) {
			packets.add(ServerboundMovePlayerPos.builder()
					.position(position)
					.onGround(player.onGround())
					.horizontalCollision(collision)
					.build());
		} else if (turned) {
			packets.add(ServerboundMovePlayerRot.builder()
					.yaw(player.yaw())
					.pitch(player.pitch())
					.onGround(player.onGround())
					.horizontalCollision(collision)
					.build());
		} else if (lastOnGround != player.onGround() || lastHorizontalCollision != collision) {
			packets.add(ServerboundMovePlayerStatusOnly.builder()
					.onGround(player.onGround())
					.horizontalCollision(collision)
					.build());
		}

		if (moved) {
			xLast = player.x();
			yLast = player.y();
			zLast = player.z();
			positionReminder = 0;
		}
		if (turned) {
			yawLast = player.yaw();
			pitchLast = player.pitch();
		}
		lastOnGround = player.onGround();
		lastHorizontalCollision = collision;
	}

	/**
	 * Remembers a movement packet sent outside a tick, such as a test's move, as the last position and rotation sent.
	 */
	void sent(double x, double y, double z, float yaw, float pitch, boolean onGround) {
		xLast = x;
		yLast = y;
		zLast = z;
		yawLast = yaw;
		pitchLast = pitch;
		positionReminder = 0;
		lastOnGround = onGround;
	}
}
