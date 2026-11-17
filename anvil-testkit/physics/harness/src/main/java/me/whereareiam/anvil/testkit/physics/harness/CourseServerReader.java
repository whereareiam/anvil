package me.whereareiam.anvil.testkit.physics.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a course's {@code [[server]]} entries, each a packet the server sends before its tick runs:
 *
 * <pre>{@code
 * [[server]]
 * tick = 30
 * teleport = { x = 4.5, y = 64.0, z = 10.5, yaw = 90.0, pitch = 0.0, relatives = [], id = 2 }
 * }</pre>
 *
 * A teleport may also give a velocity, {@code dx}, {@code dy} and {@code dz}, and name velocity components relative,
 * which only releases from Minecraft 1.21.2 carry, so such a course must declare {@code since} 1.21.2 or later.
 */
final class CourseServerReader {
	private CourseServerReader() {
	}

	static List<Course.ServerPacket> read(JsonNode rows, int ticks, String since) {
		if (rows.isMissingNode())
			return List.of();
		if (!rows.isArray())
			throw new IllegalArgumentException("server must be an array of [[server]] tables");

		List<Course.ServerPacket> packets = new ArrayList<>();
		int previous = -1;
		for (int index = 0; index < rows.size(); index++) {
			JsonNode row = rows.get(index);
			String position = "[[server]] #" + (index + 1);
			CourseReader.known(row, position, "tick", "teleport");
			int tick = CourseReader.integer(row, "tick", position);
			if (tick < previous || tick >= ticks)
				throw new IllegalArgumentException(position + " tick " + tick + " must not come before the previous entry's or after the course's last tick");
			previous = tick;
			Course.Teleport teleport = teleport(row.path("teleport"), position + " teleport");
			if (teleport.needsVelocity() && (since == null || MinecraftVersions.compare(since, "1.21.2") < 0))
				throw new IllegalArgumentException(position + " sets a velocity, which releases before Minecraft 1.21.2 cannot carry; declare since = \"1.21.2\"");
			packets.add(new Course.ServerPacket(tick, teleport));
		}
		return Collections.unmodifiableList(packets);
	}

	private static Course.Teleport teleport(JsonNode table, String context) {
		CourseReader.known(table, context, "id", "x", "y", "z", "yaw", "pitch", "dx", "dy", "dz", "relatives");
		JsonNode names = table.path("relatives");
		if (!names.isArray())
			throw new IllegalArgumentException(context + " must list its relatives, [] when none");
		Set<CourseRelative> relatives = EnumSet.noneOf(CourseRelative.class);
		for (JsonNode name : names)
			if (!relatives.add(CourseRelative.named(name.asText())))
				throw new IllegalArgumentException(context + " names " + name.asText() + " twice");

		return new Course.Teleport(CourseReader.integer(table, "id", context), CourseReader.number(table, "x", context),
			CourseReader.number(table, "y", context), CourseReader.number(table, "z", context),
			(float) CourseReader.number(table, "yaw", context), (float) CourseReader.number(table, "pitch", context),
			optional(table, "dx", context), optional(table, "dy", context), optional(table, "dz", context),
			Collections.unmodifiableSet(relatives));
	}

	private static double optional(JsonNode table, String field, String context) {
		return table.has(field) ? CourseReader.number(table, field, context) : 0.0;
	}
}
