package me.whereareiam.anvil.testkit.physics.harness.v1_16;

import me.whereareiam.anvil.testkit.physics.harness.PacketLine;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Writes the packets the vanilla clients of Minecraft 1.16.5 to 1.18.2 send as the lines of a trace, by the names and
 * fields of Anvil's protocol packets; their movement packets carry no horizontal collision. Only the packets movement
 * concerns are written; those it does not, the client's settings and brand, are left out by name, and any other
 * packet fails the harness, so none is left out by accident.
 */
final class V1_16Packets {
	private static final Set<Class<?>> LEFT_OUT = Set.of(ServerboundClientInformationPacket.class, ServerboundCustomPayloadPacket.class);
	private static final Set<ServerboundPlayerCommandPacket.Action> COMMANDS = EnumSet.of(
		ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY,
		ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY,
		ServerboundPlayerCommandPacket.Action.START_SPRINTING,
		ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
	);

	private V1_16Packets() {
	}

	/**
	 * Returns the line of a packet the client sent, or nothing for a packet movement does not concern.
	 *
	 * @throws IllegalStateException for a packet the harness neither writes nor leaves out
	 */
	static Optional<PacketLine> line(Packet<?> packet) {
		if (LEFT_OUT.contains(packet.getClass()))
			return Optional.empty();
		if (packet instanceof ServerboundAcceptTeleportationPacket)
			return Optional.of(PacketLine.of("ServerboundAcceptTeleportation").with("id", ((ServerboundAcceptTeleportationPacket) packet).getId()));
		if (packet instanceof ServerboundMovePlayerPacket)
			return Optional.of(move((ServerboundMovePlayerPacket) packet));
		if (packet instanceof ServerboundPlayerCommandPacket)
			return Optional.of(command((ServerboundPlayerCommandPacket) packet));
		if (packet instanceof ServerboundPlayerAbilitiesPacket)
			return Optional.of(PacketLine.of("ServerboundPlayerAbilities").with("flying", ((ServerboundPlayerAbilitiesPacket) packet).isFlying()));
		throw new IllegalStateException("The client sent " + packet.getClass().getName() + ", which the harness neither writes nor leaves out");
	}

	private static PacketLine move(ServerboundMovePlayerPacket move) {
		String kind = move.hasPos ? move.hasRot ? "PosRot" : "Pos" : move.hasRot ? "Rot" : "StatusOnly";
		PacketLine line = PacketLine.of("ServerboundMovePlayer" + kind);
		if (move.hasPos)
			line.with("x", move.getX(0.0)).with("y", move.getY(0.0)).with("z", move.getZ(0.0));
		if (move.hasRot)
			line.with("yaw", move.getYRot(0.0F)).with("pitch", move.getXRot(0.0F));
		return line.with("onGround", move.isOnGround());
	}

	private static PacketLine command(ServerboundPlayerCommandPacket command) {
		if (!COMMANDS.contains(command.getAction()) || command.getData() != 0)
			throw new IllegalStateException("The client sent the player command " + command.getAction() + " with data "
				+ command.getData() + ", which Anvil's player command cannot carry");
		return PacketLine.of("ServerboundPlayerCommand").with("entityId", command.id).with("action", command.getAction());
	}
}
