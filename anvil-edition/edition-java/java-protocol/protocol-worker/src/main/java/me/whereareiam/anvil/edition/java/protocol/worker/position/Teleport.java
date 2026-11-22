package me.whereareiam.anvil.edition.java.protocol.worker.position;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * The one owner of the teleport math: it resolves a server teleport from where the client is, with its view and
 * velocity, as {@code PositionMoveRotation.calculateAbsolute} does. Before Minecraft 1.21.2 the packet's velocity is
 * zero and relative along each relative coordinate, which keeps the velocity there and stops it elsewhere, as those
 * clients do.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Teleport {
	/**
	 * Resolves where a teleport puts the client, its view and the velocity it keeps.
	 *
	 * @param from where the client is, the origin while it knows no position
	 * @param view where the client looks
	 * @param velocity the client's velocity
	 * @param teleport the server's teleport
	 * @param behaviours the behaviours of the client's version
	 * @return the client's position, view and velocity afterwards
	 */
	static @NotNull Resolved resolve(
			@NotNull Vec3d from,
			@NotNull ViewRotation view,
			@NotNull Vec3d velocity,
			@NotNull ClientboundPlayerPosition teleport,
			@NotNull Set<LocalPlayerBehaviour> behaviours
	) {
		Set<Relative> relatives = teleport.getRelatives();
		float yaw = (relatives.contains(Relative.YAW) ? view.getYaw() : 0.0F) + teleport.getYaw();
		float pitch = (relatives.contains(Relative.PITCH) ? view.getPitch() : 0.0F) + teleport.getPitch();
		if (behaviours.contains(LocalPlayerBehaviour.CLAMPED_TELEPORT_PITCH)) pitch = Math.clamp(pitch, -90.0F, 90.0F);

		Vec3d delta = velocity;
		if (relatives.contains(Relative.ROTATE_DELTA))
			delta = yRot(xRot(delta, (float) Math.toRadians(view.getPitch() - pitch)),
					(float) Math.toRadians(view.getYaw() - yaw));

		Vec3d to = teleport.getPosition();
		Vec3d sent = teleport.getDeltaMovement();
		Vec3d at = new Vec3d(
				base(relatives, Relative.X, from.getX()) + to.getX(),
				base(relatives, Relative.Y, from.getY()) + to.getY(),
				base(relatives, Relative.Z, from.getZ()) + to.getZ()
		);
		Vec3d kept = new Vec3d(
				delta(relatives, Relative.DELTA_X, delta.getX(), sent.getX()),
				delta(relatives, Relative.DELTA_Y, delta.getY(), sent.getY()),
				delta(relatives, Relative.DELTA_Z, delta.getZ(), sent.getZ())
		);

		return new Resolved(at, new ViewRotation(yaw, pitch), kept);
	}

	/**
	 * Turns a velocity about the east-west axis as {@code Vec3.xRot} does, with the sine of the releases that rotate a
	 * teleport's velocity, all from 1.21.2.
	 */
	private static @NotNull Vec3d xRot(@NotNull Vec3d vector, float radians) {
		float cos = Trigonometry.DOUBLE.cos(radians);
		float sin = Trigonometry.DOUBLE.sin(radians);
		return new Vec3d(vector.getX(), vector.getY() * cos + vector.getZ() * sin, vector.getZ() * cos - vector.getY() * sin);
	}

	/**
	 * Turns a velocity about the vertical axis as {@code Vec3.yRot} does.
	 */
	private static @NotNull Vec3d yRot(@NotNull Vec3d vector, float radians) {
		float cos = Trigonometry.DOUBLE.cos(radians);
		float sin = Trigonometry.DOUBLE.sin(radians);
		return new Vec3d(vector.getX() * cos + vector.getZ() * sin, vector.getY(), vector.getZ() * cos - vector.getX() * sin);
	}

	private static double base(Set<Relative> relatives, Relative relative, double current) {
		return relatives.contains(relative) ? current : 0.0;
	}

	private static double delta(Set<Relative> relatives, Relative relative, double current, double to) {
		return relatives.contains(relative) ? current + to : to;
	}

	/**
	 * Where a teleport puts the client, its view and the velocity it keeps.
	 */
	record Resolved(@NotNull Vec3d position, @NotNull ViewRotation view, @NotNull Vec3d velocity) {
	}
}
