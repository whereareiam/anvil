package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerBody;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides the packets the vanilla client sends after each tick of its player and remembers what they told the server,
 * as {@code LocalPlayer.tick} and {@code sendPosition} do: the held keys when they changed, where the release carries
 * them ({@code ServerboundPlayerInput}), sprinting when it changed, sneaking when it changed where the release has no
 * input packet, and one movement packet: the position when it moved more than {@code 2.0E-4} or every 20 ticks, the
 * rotation when it changed, both together, or the ground state (and, from 1.21.2, the horizontal collision) when only
 * that changed. A new player told nothing, so it remembers the origin, as a new {@code LocalPlayer} does.
 */
final class PositionReporter {
	private static final double MOVED = 2.0E-4 * 2.0E-4;

	private final @NotNull MovementRules rules;
	private final @NotNull PlayerState state;
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
	 * @param state what the server set of the player, which names its entity
	 * @param inputPackets whether the release carries the held keys, which then also tell sneaking
	 */
	PositionReporter(@NotNull MovementRules rules, @NotNull PlayerState state, boolean inputPackets) {
		this.rules = rules;
		this.state = state;
		this.inputPackets = inputPackets;
	}

	/**
	 * Returns the packets for the end of a tick, in the order the client sends them, and remembers what they report.
	 */
	@NotNull List<ServerboundPacket> report(@NotNull PlayerBody player) {
		List<ServerboundPacket> packets = new ArrayList<>();
		MovementInput keys = player.keys();
		if (inputPackets && !keys.equals(lastSentInput)) {
			packets.add(input(keys));
			lastSentInput = keys;
		}
		if (player.sprinting() != wasSprinting) {
			wasSprinting = player.sprinting();
			packets.add(command(wasSprinting ? PlayerCommandAction.START_SPRINTING : PlayerCommandAction.STOP_SPRINTING));
		}
		if (!inputPackets && keys.sneak() != wasSneaking) {
			wasSneaking = keys.sneak();
			packets.add(command(wasSneaking ? PlayerCommandAction.PRESS_SHIFT_KEY : PlayerCommandAction.RELEASE_SHIFT_KEY));
		}

		movement(player, packets);
		return packets;
	}

	private void movement(@NotNull PlayerBody player, @NotNull List<ServerboundPacket> packets) {
		double dx = player.position().x - last.x();
		double dy = player.position().y - last.y();
		double dz = player.position().z - last.z();
		positionReminder++;
		boolean moved = dx * dx + dy * dy + dz * dz > MOVED || positionReminder >= 20;
		boolean turned = player.yaw() != last.yaw() || player.pitch() != last.pitch();
		boolean collision = rules.has(ClientBehaviour.REPORTED_HORIZONTAL_COLLISION) && player.horizontalCollision();
		Packet packet = Packet.of(moved, turned);
		if (packet != Packet.NONE || last.onGround() != player.onGround() || lastHorizontalCollision != collision)
			packets.add(packet.build(player, collision));

		last = Position.builder()
				.x(moved ? player.position().x : last.x())
				.y(moved ? player.position().y : last.y())
				.z(moved ? player.position().z : last.z())
				.yaw(turned ? player.yaw() : last.yaw())
				.pitch(turned ? player.pitch() : last.pitch())
				.onGround(player.onGround())
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
		last = Position.builder().x(last.x()).y(last.y()).z(last.z()).yaw(yaw).pitch(pitch).onGround(onGround)
				.build();
	}

	private @NotNull ServerboundPacket command(@NotNull PlayerCommandAction action) {
		EntityId entityId = state.entityId();
		if (entityId == null) throw new IllegalStateException("The player has not joined a level on its connection");

		return new ServerboundPlayerCommand(entityId, action);
	}

	private static @NotNull ServerboundPacket input(@NotNull MovementInput keys) {
		return ServerboundPlayerInput.builder()
				.forward(keys.forward())
				.backward(keys.backward())
				.left(keys.left())
				.right(keys.right())
				.jump(keys.jump())
				.shift(keys.sneak())
				.sprint(keys.sprint())
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
						.onGround(player.onGround())
						.horizontalCollision(collision)
						.build();
			}
		},
		POSITION {
			@Override
			ServerboundPacket build(@NotNull PlayerBody player, boolean collision) {
				return ServerboundMovePlayerPos.builder()
						.position(position(player))
						.onGround(player.onGround())
						.horizontalCollision(collision)
						.build();
			}
		},
		ROTATION {
			@Override
			ServerboundPacket build(@NotNull PlayerBody player, boolean collision) {
				return ServerboundMovePlayerRot.builder()
						.yaw(player.yaw())
						.pitch(player.pitch())
						.onGround(player.onGround())
						.horizontalCollision(collision)
						.build();
			}
		},
		BOTH {
			@Override
			ServerboundPacket build(@NotNull PlayerBody player, boolean collision) {
				return ServerboundMovePlayerPosRot.builder()
						.position(position(player))
						.yaw(player.yaw())
						.pitch(player.pitch())
						.onGround(player.onGround())
						.horizontalCollision(collision)
						.build();
			}
		};

		private static @NotNull Packet of(boolean moved, boolean turned) {
			if (moved) return turned ? BOTH : POSITION;

			return turned ? ROTATION : NONE;
		}

		private static @NotNull Vec3d position(@NotNull PlayerBody player) {
			return new Vec3d(player.position().x, player.position().y, player.position().z);
		}

		abstract ServerboundPacket build(@NotNull PlayerBody player, boolean collision);
	}
}
