package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerBody;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.java.api.LocalPlayer;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.type.player.PlayerCommandAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides the packets the vanilla client sends after each tick of its player and remembers what they told the server,
 * as {@code LocalPlayer.tick} and {@code sendPosition} do: the held keys when they changed, where the release carries
 * them ({@code ServerboundPlayerInput}), sprinting when it changed, sneaking when it changed where the release has no
 * input packet, and one movement packet: the position when it moved more than {@code 2.0E-4}, before 1.18 more than
 * {@code 0.03}, or every 20 ticks, the
 * rotation when it changed, both together, or the ground state (and, from 1.21.2, the horizontal collision) when only
 * that changed. A new player told nothing, so it remembers the origin, as a new {@code LocalPlayer} does.
 */
final class PositionReporter {
	private static final double MOVED = 2.0E-4 * 2.0E-4;
	private static final double MOVED_COARSELY = 9.0E-4;

	private final @NotNull MovementRules rules;
	private final @NotNull LocalPlayer client;
	private final boolean inputPackets;

	private @NotNull Position last = Position.builder().build();
	private int positionReminder;
	private boolean lastHorizontalCollision;
	private boolean wasSprinting;
	private boolean wasSneaking;
	private @NotNull MovementInput lastSentInput = MovementInput.NONE;

	/**
	 * Creates the reporter of a new player, which told the server nothing yet.
	 *
	 * @param rules the client's rules
	 * @param client the client's own player, which names its entity
	 * @param inputPackets whether the release carries the held keys, which then also tell sneaking
	 */
	PositionReporter(@NotNull MovementRules rules, @NotNull LocalPlayer client, boolean inputPackets) {
		this.rules = rules;
		this.client = client;
		this.inputPackets = inputPackets;
	}

	/**
	 * Returns the packets for the end of a tick, in the order the client sends them, and remembers what they report.
	 */
	@NotNull List<ServerboundPacket> report(@NotNull PlayerBody player) {
		List<ServerboundPacket> packets = new ArrayList<>();
		MovementInput keys = player.getKeys();
		if (inputPackets && !keys.equals(lastSentInput)) {
			packets.add(input(keys));
			lastSentInput = keys;
		}
		if (player.isSprinting() != wasSprinting) {
			wasSprinting = player.isSprinting();
			packets.add(command(wasSprinting ? PlayerCommandAction.START_SPRINTING : PlayerCommandAction.STOP_SPRINTING));
		}
		if (!inputPackets && keys.isSneak() != wasSneaking) {
			wasSneaking = keys.isSneak();
			packets.add(command(wasSneaking ? PlayerCommandAction.PRESS_SHIFT_KEY : PlayerCommandAction.RELEASE_SHIFT_KEY));
		}

		movement(player, packets);
		return packets;
	}

	private void movement(@NotNull PlayerBody player, @NotNull List<ServerboundPacket> packets) {
		double dx = player.getPosition().x - last.getX();
		double dy = player.getPosition().y - last.getY();
		double dz = player.getPosition().z - last.getZ();
		positionReminder++;
		double threshold = rules.has(ClientBehaviour.COARSE_POSITION_REPORT) ? MOVED_COARSELY : MOVED;
		boolean moved = dx * dx + dy * dy + dz * dz > threshold || positionReminder >= 20;
		boolean turned = player.getYaw() != last.getYaw() || player.getPitch() != last.getPitch();
		boolean collision = rules.has(ClientBehaviour.REPORTED_HORIZONTAL_COLLISION) && player.isHorizontalCollision();
		Packet packet = Packet.of(moved, turned);
		if (packet != Packet.NONE || last.isOnGround() != player.isOnGround() || lastHorizontalCollision != collision)
			packets.add(packet.build(player, collision));

		last = Position.builder()
				.x(moved ? player.getPosition().x : last.getX())
				.y(moved ? player.getPosition().y : last.getY())
				.z(moved ? player.getPosition().z : last.getZ())
				.yaw(turned ? player.getYaw() : last.getYaw())
				.pitch(turned ? player.getPitch() : last.getPitch())
				.onGround(player.isOnGround())
				.build();
		if (moved) positionReminder = 0;
		lastHorizontalCollision = collision;
	}

	/**
	 * Remembers the position and rotation a test's move sent outside a tick, as {@code sendPosition} remembers its own.
	 */
	void sent(@NotNull Position position) {
		last = position;
		positionReminder = 0;
	}

	/**
	 * Remembers the rotation a test's look sent outside a tick.
	 */
	void turned(float yaw, float pitch, boolean onGround) {
		last = Position.builder().x(last.getX()).y(last.getY()).z(last.getZ()).yaw(yaw).pitch(pitch).onGround(onGround)
				.build();
	}

	private @NotNull ServerboundPacket command(@NotNull PlayerCommandAction action) {
		EntityId entityId = client.entityId()
				.orElseThrow(() -> new IllegalStateException("The player has not joined a level on its connection"));

		return new ServerboundPlayerCommand(entityId, action);
	}

	private static @NotNull ServerboundPacket input(@NotNull MovementInput keys) {
		return ServerboundPlayerInput.builder()
				.forward(keys.isForward())
				.backward(keys.isBackward())
				.left(keys.isLeft())
				.right(keys.isRight())
				.jump(keys.isJump())
				.shift(keys.isSneak())
				.sprint(keys.isSprint())
				.build();
	}

	/**
	 * The movement packet a tick sends, by what changed.
	 */
	private enum Packet {
		NONE {
			@Override
			ServerboundPacket build(@NotNull PlayerBody player, boolean collision) {
				return ServerboundMovePlayerStatusOnly.builder()
						.onGround(player.isOnGround())
						.horizontalCollision(collision)
						.build();
			}
		},
		POSITION {
			@Override
			ServerboundPacket build(@NotNull PlayerBody player, boolean collision) {
				return ServerboundMovePlayerPos.builder()
						.position(position(player))
						.onGround(player.isOnGround())
						.horizontalCollision(collision)
						.build();
			}
		},
		ROTATION {
			@Override
			ServerboundPacket build(@NotNull PlayerBody player, boolean collision) {
				return ServerboundMovePlayerRot.builder()
						.yaw(player.getYaw())
						.pitch(player.getPitch())
						.onGround(player.isOnGround())
						.horizontalCollision(collision)
						.build();
			}
		},
		BOTH {
			@Override
			ServerboundPacket build(@NotNull PlayerBody player, boolean collision) {
				return ServerboundMovePlayerPosRot.builder()
						.position(position(player))
						.yaw(player.getYaw())
						.pitch(player.getPitch())
						.onGround(player.isOnGround())
						.horizontalCollision(collision)
						.build();
			}
		};

		private static @NotNull Packet of(boolean moved, boolean turned) {
			if (moved) return turned ? BOTH : POSITION;

			return turned ? ROTATION : NONE;
		}

		private static @NotNull Vec3d position(@NotNull PlayerBody player) {
			return new Vec3d(player.getPosition().x, player.getPosition().y, player.getPosition().z);
		}

		abstract ServerboundPacket build(@NotNull PlayerBody player, boolean collision);
	}
}
