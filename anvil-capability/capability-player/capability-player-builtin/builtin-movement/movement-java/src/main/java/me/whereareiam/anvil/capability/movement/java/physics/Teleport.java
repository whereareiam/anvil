package me.whereareiam.anvil.capability.movement.java.physics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
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
		Set<Relative> relatives = teleport.relatives();
		float yaw = (relatives.contains(Relative.YAW) ? from.yaw() : 0.0F) + teleport.yaw();
		float pitch = (relatives.contains(Relative.PITCH) ? from.pitch() : 0.0F) + teleport.pitch();
		if (rules.has(ClientBehaviour.CLAMPED_TELEPORT_PITCH)) pitch = Math.clamp(pitch, -90.0F, 90.0F);

		Vector delta = velocity;
		if (relatives.contains(Relative.ROTATE_DELTA))
			delta = delta.xRot((float) Math.toRadians(from.pitch() - pitch), Trigonometry.DOUBLE)
					.yRot((float) Math.toRadians(from.yaw() - yaw), Trigonometry.DOUBLE);

		Vec3d to = teleport.position();
		Vec3d sent = teleport.deltaMovement();
		Position at = Position.builder()
				.x(base(relatives, Relative.X, from.x()) + to.x())
				.y(base(relatives, Relative.Y, from.y()) + to.y())
				.z(base(relatives, Relative.Z, from.z()) + to.z())
				.yaw(yaw)
				.pitch(pitch)
				.onGround(from.onGround())
				.build();
		Vector kept = new Vector(
				delta(relatives, Relative.DELTA_X, delta.x, sent.x()),
				delta(relatives, Relative.DELTA_Y, delta.y, sent.y()),
				delta(relatives, Relative.DELTA_Z, delta.z, sent.z())
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
