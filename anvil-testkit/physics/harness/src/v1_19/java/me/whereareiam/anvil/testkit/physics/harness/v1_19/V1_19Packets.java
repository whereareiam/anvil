package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import me.whereareiam.anvil.testkit.physics.harness.PacketLine;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;

import java.util.Optional;
import java.util.Set;

/**
 * Writes the packets the vanilla clients of Minecraft 1.19.4 to 1.21.1 send as the lines of a trace, by the names and
 * fields of Anvil's protocol packets; their movement packets carry no horizontal collision. Only the packets movement
 * concerns, the swings the client answers the server's animations of its player with and the answers to the server's
 * pings are written; those it does not, the client's settings, brand and chat session, are left out by name
 * ({@link V1_19Client#LEFT_OUT}), and any other packet fails the harness, so none is left out by accident.
 */
final class V1_19Packets {
	private static final Set<ServerboundPlayerCommandPacket.Action> COMMANDS = Set.of(
		ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY,
		ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY,
		ServerboundPlayerCommandPacket.Action.START_SPRINTING,
		ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
	);

	private V1_19Packets() {
	}

	/**
	 * Returns the line of a packet the client sent, or nothing for a packet movement does not concern.
	 *
	 * @throws IllegalStateException for a packet the harness neither writes nor leaves out
	 */
	static Optional<PacketLine> line(Packet<?> packet) {
		if (V1_19Client.LEFT_OUT.contains(packet.getClass()))
			return Optional.empty();
		if (packet instanceof ServerboundAcceptTeleportationPacket accept)
			return Optional.of(PacketLine.of("ServerboundAcceptTeleportation").with("id", accept.getId()));
		if (packet instanceof ServerboundMovePlayerPacket move)
			return Optional.of(move(move));
		if (packet instanceof ServerboundPlayerCommandPacket command)
			return Optional.of(command(command));
		if (packet instanceof ServerboundPlayerAbilitiesPacket abilities)
			return Optional.of(PacketLine.of("ServerboundPlayerAbilities").with("flying", abilities.isFlying()));
		if (packet instanceof ServerboundSwingPacket swing)
			return Optional.of(PacketLine.of("ServerboundSwing").with("hand", swing.getHand()));
		Optional<PacketLine> answer = V1_19Client.answer(packet);
		if (answer.isPresent())
			return answer;
		throw new IllegalStateException("The client sent " + packet.getClass().getName() + ", which the harness neither writes nor leaves out");
	}

	private static PacketLine move(ServerboundMovePlayerPacket move) {
		String kind = move.hasPosition() ? move.hasRotation() ? "PosRot" : "Pos" : move.hasRotation() ? "Rot" : "StatusOnly";
		PacketLine line = PacketLine.of("ServerboundMovePlayer" + kind);
		if (move.hasPosition())
			line.with("x", move.getX(0.0)).with("y", move.getY(0.0)).with("z", move.getZ(0.0));
		if (move.hasRotation())
			line.with("yaw", move.getYRot(0.0F)).with("pitch", move.getXRot(0.0F));
		return line.with("onGround", move.isOnGround());
	}

	private static PacketLine command(ServerboundPlayerCommandPacket command) {
		if (!COMMANDS.contains(command.getAction()) || command.getData() != 0)
			throw new IllegalStateException("The client sent the player command " + command.getAction() + " with data "
				+ command.getData() + ", which Anvil's player command cannot carry");
		return PacketLine.of("ServerboundPlayerCommand").with("entityId", command.getId()).with("action", command.getAction());
	}
}
