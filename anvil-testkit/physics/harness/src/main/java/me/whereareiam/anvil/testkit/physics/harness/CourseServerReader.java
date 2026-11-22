package me.whereareiam.anvil.testkit.physics.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a course's {@code [[server]]} entries, each a packet the server sends before its tick runs, a teleport, a ping
 * or an animation of the player's own entity:
 *
 * <pre>{@code
 * [[server]]
 * tick = 30
 * ping = { id = -2 }
 *
 * [[server]]
 * tick = 30
 * teleport = { x = 4.5, y = 64.0, z = 10.5, yaw = 90.0, pitch = 0.0, relatives = [], id = 2 }
 *
 * [[server]]
 * tick = 30
 * animate = { animation = "swing_main_hand" }
 * }</pre>
 *
 * A teleport may also give a velocity, {@code dx}, {@code dy} and {@code dz}, and name velocity components relative,
 * which only releases from Minecraft 1.21.2 carry, so such a course must declare {@code since} 1.21.2 or later. A ping's
 * ID fits a short, the action number of the rejected transaction that stands for it before Minecraft 1.17. A course
 * that joins may send pings at tick {@code -1}, during the join's handshake, after the login and before the first
 * position packet, as an anticheat pings before each teleport.
 */
final class CourseServerReader {
	private CourseServerReader() {
	}

	static List<Course.ServerPacket> read(JsonNode rows, int ticks, String since, boolean joins) {
		if (rows.isMissingNode())
			return List.of();
		if (!rows.isArray())
			throw new IllegalArgumentException("server must be an array of [[server]] tables");

		List<Course.ServerPacket> packets = new ArrayList<>();
		int previous = Course.Join.HANDSHAKE;
		for (int index = 0; index < rows.size(); index++) {
			JsonNode row = rows.get(index);
			String position = "[[server]] #" + (index + 1);
			CourseReader.known(row, position, "tick", "teleport", "ping", "animate");
			int tick = CourseReader.integer(row, "tick", position);
			if (tick < previous || tick >= ticks)
				throw new IllegalArgumentException(position + " tick " + tick + " must not come before the previous entry's or after the course's last tick");
			previous = tick;
			Course.ServerPacket packet = packet(row, tick, since, position);
			if (tick == Course.Join.HANDSHAKE && (!joins || !(packet instanceof Course.Ping)))
				throw new IllegalArgumentException(position + " at tick " + tick + " must be a ping of a course that joins");
			packets.add(packet);
		}
		return Collections.unmodifiableList(packets);
	}

	private static Course.ServerPacket packet(JsonNode row, int tick, String since, String position) {
		int kinds = (row.has("teleport") ? 1 : 0) + (row.has("ping") ? 1 : 0) + (row.has("animate") ? 1 : 0);
		if (kinds != 1)
			throw new IllegalArgumentException(position + " must send one teleport, ping or animation");
		if (row.has("ping"))
			return ping(row.path("ping"), tick, position + " ping");
		if (row.has("animate"))
			return animate(row.path("animate"), tick, position + " animate");
		return teleport(row.path("teleport"), tick, since, position + " teleport");
	}

	private static Course.Animate animate(JsonNode table, int tick, String context) {
		CourseReader.known(table, context, "animation");
		return new Course.Animate(tick, CourseAnimation.named(CourseReader.text(table, "animation", context)));
	}

	private static Course.Ping ping(JsonNode table, int tick, String context) {
		CourseReader.known(table, context, "id");
		int id = CourseReader.integer(table, "id", context);
		if (id < Short.MIN_VALUE || id > Short.MAX_VALUE)
			throw new IllegalArgumentException(context + " id " + id + " must fit a short, a transaction's action number");
		return new Course.Ping(tick, (short) id);
	}

	private static Course.Teleport teleport(JsonNode table, int tick, String since, String context) {
		Course.Teleport teleport = teleport(table, tick, context);
		if (teleport.needsVelocity() && (since == null || MinecraftVersions.compare(since, "1.21.2") < 0))
			throw new IllegalArgumentException(context + " sets a velocity, which releases before Minecraft 1.21.2 cannot carry; declare since = \"1.21.2\"");
		return teleport;
	}

	private static Course.Teleport teleport(JsonNode table, int tick, String context) {
		CourseReader.known(table, context, "id", "x", "y", "z", "yaw", "pitch", "dx", "dy", "dz", "relatives");
		JsonNode names = table.path("relatives");
		if (!names.isArray())
			throw new IllegalArgumentException(context + " must list its relatives, [] when none");
		Set<CourseRelative> relatives = EnumSet.noneOf(CourseRelative.class);
		for (JsonNode name : names)
			if (!relatives.add(CourseRelative.named(name.asText())))
				throw new IllegalArgumentException(context + " names " + name.asText() + " twice");

		return new Course.Teleport(tick, CourseReader.integer(table, "id", context), CourseReader.number(table, "x", context),
			CourseReader.number(table, "y", context), CourseReader.number(table, "z", context),
			(float) CourseReader.number(table, "yaw", context), (float) CourseReader.number(table, "pitch", context),
			optional(table, "dx", context), optional(table, "dy", context), optional(table, "dz", context),
			Collections.unmodifiableSet(relatives));
	}

	private static double optional(JsonNode table, String field, String context) {
		return table.has(field) ? CourseReader.number(table, field, context) : 0.0;
	}
}
