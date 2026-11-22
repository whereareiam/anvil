package me.whereareiam.anvil.testkit.physics.harness.v1_21_11;

import me.whereareiam.anvil.testkit.physics.harness.PacketLine;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.entity.player.Input;

import java.util.Optional;
import java.util.Set;

/**
 * Writes the packets the vanilla client of Minecraft 1.21.11 sends as the lines of a trace, by the names and fields of
 * Anvil's protocol packets. Only the packets movement concerns are written; those it does not, the client's settings,
 * brand and chat session, are left out by name, and any other packet fails the harness, so none is left out by
 * accident.
 */
final class V1_21_11Packets {
	private static final Set<Class<?>> LEFT_OUT = Set.of(
		ServerboundClientInformationPacket.class,
		ServerboundCustomPayloadPacket.class,
		ServerboundChatSessionUpdatePacket.class
	);

	private V1_21_11Packets() {
	}

	/**
	 * Returns the line of a packet the client sent, or nothing for a packet movement does not concern.
	 *
	 * @throws IllegalStateException for a packet the harness neither writes nor leaves out
	 */
	static Optional<PacketLine> line(Packet<?> packet) {
		if (LEFT_OUT.contains(packet.getClass()))
			return Optional.empty();
		return Optional.of(switch (packet) {
			case ServerboundAcceptTeleportationPacket accept -> PacketLine.of("ServerboundAcceptTeleportation").with("id", accept.getId());
			case ServerboundMovePlayerPacket move -> move(move);
			case ServerboundPlayerInputPacket input -> input(input.input());
			case ServerboundPlayerCommandPacket command -> command(command);
			case ServerboundPlayerAbilitiesPacket abilities -> PacketLine.of("ServerboundPlayerAbilities").with("flying", abilities.isFlying());
			case ServerboundClientTickEndPacket ignored -> PacketLine.of("ServerboundClientTickEnd");
			case ServerboundPlayerLoadedPacket ignored -> PacketLine.of("ServerboundPlayerLoaded");
			case ServerboundPongPacket pong -> PacketLine.of("ServerboundPong").with("id", pong.getId());
			case ServerboundSwingPacket swing -> PacketLine.of("ServerboundSwing").with("hand", swing.getHand());
			default -> throw new IllegalStateException("The client sent " + packet.getClass().getName()
				+ ", which the harness neither writes nor leaves out");
		});
	}

	private static PacketLine move(ServerboundMovePlayerPacket move) {
		String kind = move.hasPosition() ? move.hasRotation() ? "PosRot" : "Pos" : move.hasRotation() ? "Rot" : "StatusOnly";
		PacketLine line = PacketLine.of("ServerboundMovePlayer" + kind);
		if (move.hasPosition())
			line.with("x", move.getX(0.0)).with("y", move.getY(0.0)).with("z", move.getZ(0.0));
		if (move.hasRotation())
			line.with("yaw", move.getYRot(0.0F)).with("pitch", move.getXRot(0.0F));
		return line.with("onGround", move.isOnGround()).with("horizontalCollision", move.horizontalCollision());
	}

	private static PacketLine input(Input input) {
		return PacketLine.of("ServerboundPlayerInput").with("forward", input.forward()).with("backward", input.backward())
			.with("left", input.left()).with("right", input.right()).with("jump", input.jump()).with("shift", input.shift())
			.with("sprint", input.sprint());
	}

	private static PacketLine command(ServerboundPlayerCommandPacket command) {
		if (command.getData() != 0)
			throw new IllegalStateException("The client sent " + command.getAction() + " with data " + command.getData()
				+ ", which Anvil's player command cannot carry");
		if (command.getAction() != ServerboundPlayerCommandPacket.Action.START_SPRINTING
			&& command.getAction() != ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)
			throw new IllegalStateException("The client sent the player command " + command.getAction()
				+ ", which Anvil's player command cannot carry");
		return PacketLine.of("ServerboundPlayerCommand").with("entityId", command.getId()).with("action", command.getAction());
	}
}
