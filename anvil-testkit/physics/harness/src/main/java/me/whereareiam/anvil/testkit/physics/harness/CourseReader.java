package me.whereareiam.anvil.testkit.physics.harness;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the courses of {@code anvil-testkit/physics/courses}, one {@code <name>.toml} each, and refuses anything the
 * course format does not define, so a misspelt field never silently falls back to a default. A course is placed at its
 * {@code [start]} or joins as its {@code [join]} says, and may name the first Minecraft version it runs on,
 * {@code since}; {@link CourseServerReader} reads what the server sends.
 */
public final class CourseReader {
	private static final String EXTENSION = ".toml";

	private final TomlMapper mapper = new TomlMapper();

	/**
	 * Reads every course in {@code directory}, ordered by name.
	 *
	 * @param directory the courses directory
	 * @return the courses
	 * @throws IOException when a file cannot be read or is no valid course
	 */
	public List<Course> readAll(Path directory) throws IOException {
		List<Path> files;
		try (Stream<Path> listing = Files.list(directory)) {
			files = listing.filter(file -> file.getFileName().toString().endsWith(EXTENSION)).sorted().collect(Collectors.toList());
		}
		if (files.isEmpty())
			throw new IOException(directory + " holds no course");

		List<Course> courses = new ArrayList<>();
		for (Path file : files)
			courses.add(read(file));
		return courses;
	}

	/**
	 * Reads the course in {@code file}.
	 *
	 * @param file the course file
	 * @return the course
	 * @throws IOException when the file cannot be read or is no valid course
	 */
	public Course read(Path file) throws IOException {
		String fileName = file.getFileName().toString();
		String name = fileName.substring(0, fileName.length() - EXTENSION.length());
		JsonNode document;
		try {
			document = mapper.readTree(file.toFile());
		} catch (JacksonException exception) {
			throw new IOException(fileName + " is not valid TOML: " + exception.getOriginalMessage(), exception);
		}
		try {
			return course(name, document);
		} catch (IllegalArgumentException exception) {
			throw new IOException(fileName + ": " + exception.getMessage(), exception);
		}
	}

	private Course course(String name, JsonNode document) {
		known(document, "the course", "description", "since", "ticks", "start", "join", "blocks", "keys", "server");
		int ticks = integer(document, "ticks", "the course");
		if (ticks < 1)
			throw new IllegalArgumentException("ticks must be positive");
		if (document.has("start") == document.has("join"))
			throw new IllegalArgumentException("the course must declare either a [start] table or a [join] table");

		String since = document.has("since") ? text(document, "since", "the course") : null;
		Course.Start start = document.has("start") ? start(table(document, "start")) : null;
		Course.Join join = document.has("join") ? join(table(document, "join"), ticks) : null;
		List<Course.ServerPacket> server = CourseServerReader.read(document.path("server"), ticks, since, join != null);
		return new Course(name, text(document, "description", "the course"), since, ticks, start, join,
			blocks(document.path("blocks")), keys(document.path("keys"), ticks), server);
	}

	private Course.Start start(JsonNode table) {
		known(table, "[start]", "x", "y", "z", "yaw", "pitch");
		return new Course.Start(number(table, "x", "[start]"), number(table, "y", "[start]"), number(table, "z", "[start]"),
			(float) number(table, "yaw", "[start]"), (float) number(table, "pitch", "[start]"));
	}

	private Course.Join join(JsonNode table, int ticks) {
		known(table, "[join]", "x", "y", "z", "yaw", "pitch", "chunksAfterTicks");
		int chunksAfterTicks = integer(table, "chunksAfterTicks", "[join]");
		if (chunksAfterTicks < 0 || chunksAfterTicks >= ticks)
			throw new IllegalArgumentException("[join] chunksAfterTicks must name one of the course's ticks");
		return new Course.Join(number(table, "x", "[join]"), number(table, "y", "[join]"), number(table, "z", "[join]"),
			(float) number(table, "yaw", "[join]"), (float) number(table, "pitch", "[join]"), chunksAfterTicks);
	}

	private List<Course.Box> blocks(JsonNode rows) {
		if (rows.isMissingNode())
			return List.of();
		if (!rows.isArray())
			throw new IllegalArgumentException("blocks must be an array of [[blocks]] tables");

		List<Course.Box> boxes = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			JsonNode row = rows.get(index);
			String position = "[[blocks]] #" + (index + 1);
			known(row, position, "from", "to", "state");
			int[] from = corner(row, "from", position);
			int[] to = corner(row, "to", position);
			for (int axis = 0; axis < 3; axis++)
				if (from[axis] > to[axis])
					throw new IllegalArgumentException(position + " from must not exceed to on any axis");
			BlockStateText state = BlockStateText.parse(text(row, "state", position));
			boxes.add(new Course.Box(from[0], from[1], from[2], to[0], to[1], to[2], state));
		}
		return Collections.unmodifiableList(boxes);
	}

	private List<Course.KeyChange> keys(JsonNode rows, int ticks) {
		if (rows.isMissingNode())
			return List.of();
		if (!rows.isArray())
			throw new IllegalArgumentException("keys must be an array of [[keys]] tables");

		List<Course.KeyChange> changes = new ArrayList<>();
		int previous = -1;
		for (int index = 0; index < rows.size(); index++) {
			JsonNode row = rows.get(index);
			String position = "[[keys]] #" + (index + 1);
			known(row, position, "tick", "held");
			int tick = integer(row, "tick", position);
			if (tick <= previous)
				throw new IllegalArgumentException(position + " tick " + tick + " must come after the previous entry's");
			if (tick >= ticks)
				throw new IllegalArgumentException(position + " tick " + tick + " is past the course's last tick");
			previous = tick;

			JsonNode held = row.path("held");
			if (!held.isArray())
				throw new IllegalArgumentException(position + " must list its held keys");
			Set<CourseKey> keys = EnumSet.noneOf(CourseKey.class);
			for (JsonNode key : held) {
				if (!key.isTextual())
					throw new IllegalArgumentException(position + " held must list key names");
				if (!keys.add(CourseKey.named(key.asText())))
					throw new IllegalArgumentException(position + " holds " + key.asText() + " twice");
			}
			changes.add(new Course.KeyChange(tick, Collections.unmodifiableSet(keys)));
		}
		return Collections.unmodifiableList(changes);
	}

	static int[] corner(JsonNode row, String field, String position) {
		JsonNode corner = row.path(field);
		if (!corner.isArray() || corner.size() != 3)
			throw new IllegalArgumentException(position + " " + field + " must be [x, y, z]");
		int[] coordinates = new int[3];
		for (int axis = 0; axis < 3; axis++) {
			if (!corner.get(axis).isIntegralNumber())
				throw new IllegalArgumentException(position + " " + field + " must hold whole block coordinates");
			coordinates[axis] = corner.get(axis).asInt();
		}
		return coordinates;
	}

	private static JsonNode table(JsonNode document, String field) {
		JsonNode table = document.path(field);
		if (!table.isObject())
			throw new IllegalArgumentException("the course must declare a [" + field + "] table");
		return table;
	}

	static String text(JsonNode node, String field, String context) {
		JsonNode value = node.path(field);
		if (!value.isTextual() || value.asText().isBlank())
			throw new IllegalArgumentException(context + " must declare a non-blank " + field);
		return value.asText();
	}

	static int integer(JsonNode node, String field, String context) {
		JsonNode value = node.path(field);
		if (!value.isIntegralNumber())
			throw new IllegalArgumentException(context + " must declare " + field + " as a whole number");
		return value.asInt();
	}

	static double number(JsonNode node, String field, String context) {
		JsonNode value = node.path(field);
		if (!value.isNumber())
			throw new IllegalArgumentException(context + " must declare " + field + " as a number");
		return value.asDouble();
	}

	static void known(JsonNode node, String context, String... fields) {
		if (!node.isObject())
			throw new IllegalArgumentException(context + " must be a table");
		List<String> allowed = List.of(fields);
		for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
			String name = names.next();
			if (!allowed.contains(name))
				throw new IllegalArgumentException(context + " has unknown field " + name + "; expected " + allowed);
		}
	}
}
