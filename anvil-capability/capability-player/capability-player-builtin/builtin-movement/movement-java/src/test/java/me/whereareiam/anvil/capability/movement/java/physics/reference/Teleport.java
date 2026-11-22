package me.whereareiam.anvil.capability.movement.java.physics.reference;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * A teleport the server of a course sends before a tick runs, as {@code [[server]]} names it.
 *
 * @param tick the tick it comes before
 * @param id the teleport's ID
 * @param x the position's x, added to the current one when {@code relatives} names it
 * @param y the position's y
 * @param z the position's z
 * @param yaw the horizontal view direction
 * @param pitch the vertical view direction
 * @param delta the velocity, which releases from Minecraft 1.21.2 carry
 * @param relatives what is relative to the current values
 */
record Teleport(int tick, int id, double x, double y, double z, float yaw, float pitch, @NotNull Vec3d delta,
		@NotNull Set<Relative> relatives) implements ServerPacket {
	private static final MinecraftVersion VELOCITY = MinecraftVersion.parse("1.21.2");

	/**
	 * Returns the first position packet of a join, teleport ID {@code 1}, which places the player absolutely.
	 */
	static @NotNull Teleport first(@NotNull Position at) {
		return new Teleport(-1, 1, at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch(), Vec3d.ZERO, Set.of());
	}

	/**
	 * Reads a {@code [[server]]} entry.
	 */
	static @NotNull Teleport read(@NotNull JsonNode entry) {
		JsonNode teleport = entry.path("teleport");
		Set<Relative> relatives = EnumSet.noneOf(Relative.class);
		for (JsonNode name : teleport.path("relatives")) relatives.add(Relative.valueOf(name.asText().toUpperCase(Locale.ROOT)));
		return new Teleport(entry.path("tick").asInt(), teleport.path("id").asInt(), teleport.path("x").asDouble(),
				teleport.path("y").asDouble(), teleport.path("z").asDouble(), (float) teleport.path("yaw").asDouble(),
				(float) teleport.path("pitch").asDouble(), new Vec3d(teleport.path("dx").asDouble(),
				teleport.path("dy").asDouble(), teleport.path("dz").asDouble()), Set.copyOf(relatives));
	}

	/**
	 * Returns the position packet as the player's library reports it for a release: before Minecraft 1.21.2 it carries
	 * no velocity, which the protocol reports as a zero velocity relative along each relative coordinate.
	 *
	 * @param release the release's Minecraft version
	 * @return the packet
	 */
	@Override
	public @NotNull ClientboundPlayerPosition packet(@NotNull MinecraftVersion release) {
		Set<Relative> reported = EnumSet.noneOf(Relative.class);
		reported.addAll(relatives);
		if (!release.isAtLeast(VELOCITY)) {
			if (relatives.contains(Relative.X)) reported.add(Relative.DELTA_X);
			if (relatives.contains(Relative.Y)) reported.add(Relative.DELTA_Y);
			if (relatives.contains(Relative.Z)) reported.add(Relative.DELTA_Z);
		}
		return ClientboundPlayerPosition.builder().id(id).position(new Vec3d(x, y, z)).yaw(yaw).pitch(pitch)
				.deltaMovement(delta).relatives(reported).build();
	}
}
