package me.whereareiam.anvil.testkit.tests.server.physics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Reads the physics courses of {@code anvil-testkit/physics/courses} strictly: a file that is not a valid course fails
 * the suite rather than run a course other than the one it declares.
 */
final class CourseReader {
	private static final String EXTENSION = ".toml";
	private static final Set<String> KEYS = Set.of("forward", "backward", "left", "right", "jump", "sneak", "sprint");

	private CourseReader() {
	}

	/**
	 * Reads every course of the directory a system property names.
	 *
	 * @param property the system property naming the courses' directory
	 * @return the courses, ordered by name
	 * @throws IllegalStateException when the property is not set, or the directory holds no course or an invalid one
	 */
	static @NotNull List<Course> all(@NotNull String property) {
		String directory = System.getProperty(property);
		if (directory == null || directory.isBlank())
			throw new IllegalStateException("The physics suite needs the courses' directory in the system property "
					+ property + ", which the physicsTest-<minecraft> tasks set");

		List<Course> courses;
		try (Stream<Path> files = Files.list(Path.of(directory))) {
			courses = files.filter(file -> file.getFileName().toString().endsWith(EXTENSION))
					.sorted()
					.map(CourseReader::read)
					.toList();
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not list the physics courses in " + directory, failure);
		}
		if (courses.isEmpty()) throw new IllegalStateException("No physics course in " + directory);

		return courses;
	}

	/**
	 * Reads one course file.
	 *
	 * @param file the course's TOML file
	 * @return the course
	 * @throws IllegalStateException when the file is not a valid course
	 */
	static @NotNull Course read(@NotNull Path file) {
		String fileName = file.getFileName().toString();
		String name = fileName.substring(0, fileName.length() - EXTENSION.length());
		JsonNode root;
		try {
			root = new TomlMapper().readTree(file.toFile());
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read the physics course " + file, failure);
		}

		int ticks = integer(root, "ticks", name);
		if (ticks < 1) throw invalid(name, "'ticks' must be positive, not " + ticks);

		List<Course.Box> blocks = new ArrayList<>();
		for (JsonNode box : root.path("blocks"))
			blocks.add(new Course.Box(corner(box, "from", name), corner(box, "to", name), text(box, "state", name)));
		if (blocks.isEmpty()) throw invalid(name, "it lays no blocks");

		List<Course.Keys> keys = new ArrayList<>();
		for (JsonNode entry : root.path("keys"))
			keys.add(keys(entry, ticks, keys.isEmpty() ? -1 : keys.getLast().tick(), name));

		JsonNode since = root.get("since");
		return new Course(name, text(root, "description", name),
				since == null ? null : MinecraftVersion.parse(text(root, "since", name)), ticks, start(root, name),
				List.copyOf(blocks), List.copyOf(keys), scriptsTeleports(root.path("server")));
	}

	/**
	 * Reads where the server places the player: the course's {@code [start]}, or the position its {@code [join]}'s
	 * first position packet sets.
	 */
	private static Course.Start start(JsonNode root, String course) {
		boolean placed = root.has("start");
		if (placed == root.has("join")) throw invalid(course, "it needs either [start] or [join]");

		String table = placed ? "start" : "join";
		JsonNode start = root.path(table);
		return new Course.Start(decimal(start, table, "x", course), decimal(start, table, "y", course),
				decimal(start, table, "z", course), (float) decimal(start, table, "yaw", course),
				(float) decimal(start, table, "pitch", course));
	}

	private static Course.Keys keys(JsonNode entry, int ticks, int previous, String course) {
		int tick = integer(entry, "tick", course);
		if (tick <= previous || tick >= ticks)
			throw invalid(course, "key entries must be at increasing ticks below " + ticks + ", not at " + tick);

		Set<String> held = new TreeSet<>();
		for (JsonNode key : entry.path("held")) {
			if (!KEYS.contains(key.asText())) throw invalid(course, "'" + key.asText() + "' is not a key of " + KEYS);
			held.add(key.asText());
		}

		return new Course.Keys(tick, MovementInput.builder()
				.forward(held.contains("forward"))
				.backward(held.contains("backward"))
				.left(held.contains("left"))
				.right(held.contains("right"))
				.jump(held.contains("jump"))
				.sneak(held.contains("sneak"))
				.sprint(held.contains("sprint"))
				.build());
	}

	private static int[] corner(JsonNode box, String field, String course) {
		JsonNode value = box.get(field);
		if (value == null || !value.isArray() || value.size() != 3)
			throw invalid(course, "a box needs '" + field + "' as three block coordinates");

		int[] corner = new int[3];
		for (int axis = 0; axis < 3; axis++) {
			if (!value.get(axis).isIntegralNumber()) throw invalid(course, "'" + field + "' holds " + value);
			corner[axis] = value.get(axis).asInt();
		}

		return corner;
	}

	private static int integer(JsonNode node, String field, String course) {
		JsonNode value = node.get(field);
		if (value == null || !value.isIntegralNumber()) throw invalid(course, "it needs an integer '" + field + "'");

		return value.asInt();
	}

	private static double decimal(JsonNode node, String table, String field, String course) {
		JsonNode value = node.get(field);
		if (value == null || !value.isNumber())
			throw invalid(course, "it needs a number '" + field + "' in [" + table + "]");

		return value.asDouble();
	}

	private static String text(JsonNode node, String field, String course) {
		JsonNode value = node.get(field);
		if (value == null || !value.isTextual() || value.asText().isBlank())
			throw invalid(course, "it needs a text '" + field + "'");

		return value.asText();
	}

	private static IllegalStateException invalid(String course, String reason) {
		return new IllegalStateException("The physics course '" + course + "' is invalid: " + reason);
	}

	/**
	 * Tells whether a course's {@code [[server]]} entries send a teleport; a ping or an animation leaves the
	 * player where it is.
	 */
	private static boolean scriptsTeleports(@NotNull JsonNode server) {
		for (JsonNode entry : server)
			if (entry.has("teleport")) return true;

		return false;
	}
}
