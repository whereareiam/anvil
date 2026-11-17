package me.whereareiam.anvil.capability.movement.java.physics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * The one owner of the teleport math: it resolves a server teleport from one base, where the client is with its
 * simulated velocity, as {@code PositionMoveRotation.calculateAbsolute} does. Before Minecraft 1.21.2 the packet's
 * velocity is zero and relative along each relative coordinate, which keeps the velocity there and stops it elsewhere,
 * as those clients do.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Teleport {
	/**
	 * Resolves where a teleport puts the client and the velocity it keeps.
	 *
	 * @param from where the client is
	 * @param velocity the client's simulated velocity
	 * @param teleport the server's teleport
	 * @param rules the behaviours of the client's version
	 * @return the client's position, view and velocity afterwards
	 */
	static @NotNull Resolved resolve(
			@NotNull Position from,
			@NotNull Vector velocity,
			@NotNull ClientboundPlayerPosition teleport,
			@NotNull MovementRules rules
	) {
		Set<Relative> relatives = teleport.getRelatives();
		float yaw = (relatives.contains(Relative.YAW) ? from.getYaw() : 0.0F) + teleport.getYaw();
		float pitch = (relatives.contains(Relative.PITCH) ? from.getPitch() : 0.0F) + teleport.getPitch();
		if (rules.has(ClientBehaviour.CLAMPED_TELEPORT_PITCH)) pitch = Math.clamp(pitch, -90.0F, 90.0F);

		Vector delta = velocity;
		if (relatives.contains(Relative.ROTATE_DELTA))
			delta = delta.xRot((float) Math.toRadians(from.getPitch() - pitch), Trigonometry.DOUBLE)
					.yRot((float) Math.toRadians(from.getYaw() - yaw), Trigonometry.DOUBLE);

		Vec3d to = teleport.getPosition();
		Vec3d sent = teleport.getDeltaMovement();
		Position at = Position.builder()
				.x(base(relatives, Relative.X, from.getX()) + to.getX())
				.y(base(relatives, Relative.Y, from.getY()) + to.getY())
				.z(base(relatives, Relative.Z, from.getZ()) + to.getZ())
				.yaw(yaw)
				.pitch(pitch)
				.onGround(from.isOnGround())
				.build();
		Vector kept = new Vector(
				delta(relatives, Relative.DELTA_X, delta.x, sent.getX()),
				delta(relatives, Relative.DELTA_Y, delta.y, sent.getY()),
				delta(relatives, Relative.DELTA_Z, delta.z, sent.getZ())
		);

		return new Resolved(at, kept);
	}

	private static double base(Set<Relative> relatives, Relative relative, double current) {
		return relatives.contains(relative) ? current : 0.0;
	}

	private static double delta(Set<Relative> relatives, Relative relative, double current, double to) {
		return relatives.contains(relative) ? current + to : to;
	}

	/**
	 * Where a teleport puts the client, with its view, and the velocity it keeps.
	 */
	record Resolved(@NotNull Position at, @NotNull Vector velocity) {
	}
}
