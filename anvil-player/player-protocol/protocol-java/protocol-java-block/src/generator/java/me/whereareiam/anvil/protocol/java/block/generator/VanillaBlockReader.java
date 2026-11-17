package me.whereareiam.anvil.protocol.java.block.generator;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.block.model.BlockProperty;
import me.whereareiam.anvil.protocol.java.block.model.BlockState;
import me.whereareiam.anvil.protocol.java.block.model.BlockType;
import me.whereareiam.anvil.protocol.java.block.model.collision.BlockOffset;
import me.whereareiam.anvil.protocol.java.block.model.collision.CollisionBox;
import me.whereareiam.anvil.protocol.java.block.model.collision.CollisionCase;
import me.whereareiam.anvil.protocol.java.block.model.collision.CollisionShape;
import me.whereareiam.anvil.protocol.java.block.model.movement.BlockMovement;
import me.whereareiam.anvil.protocol.java.block.model.movement.StuckSpeed;
import me.whereareiam.anvil.protocol.java.block.type.BlockFace;
import me.whereareiam.anvil.protocol.java.block.type.BlockKind;
import me.whereareiam.anvil.protocol.java.block.type.CollisionKind;
import me.whereareiam.anvil.protocol.java.block.type.EntityBottom;
import me.whereareiam.anvil.protocol.java.block.type.FluidType;
import me.whereareiam.anvil.protocol.java.block.type.MovementBehaviour;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the block data the physics harness's {@code generateVanillaData} writes from a vanilla client,
 * {@code <protocol>.blocks}: text whose format the harness's {@code BlockDataWriter} documents, every number exact in
 * Java's hexadecimal floating-point form. It reads strictly: an unknown or repeated line, a missing line, a count that
 * does not match or a shape that does not exist fails with the file's line.
 */
public final class VanillaBlockReader {
	/**
	 * The lines a block has at most once.
	 */
	private static final Set<String> FIELDS = Set.of("states", "kind", "collision", "offset", "movement", "stuck", "behaviours", "tags",
			"collisions", "fluids", "flow", "sturdy");

	private final @NotNull String context;
	private final @NotNull BufferedReader lines;
	private int lineNumber;

	private VanillaBlockReader(@NotNull String context, @NotNull BufferedReader lines) {
		this.context = context;
		this.lines = lines;
	}

	/**
	 * Reads a block data file.
	 *
	 * @param file the file
	 * @return its blocks and states
	 * @throws IOException when the file cannot be read
	 * @throws IllegalStateException when the file breaks the format
	 */
	public static @NotNull Read read(@NotNull Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return new VanillaBlockReader(file.getFileName().toString(), reader).read();
		}
	}

	/**
	 * Reads block data from text, named {@code context} in failures.
	 *
	 * @param context what the text is, such as its file name
	 * @param text    the block data
	 * @return its blocks and states
	 * @throws IllegalStateException when the text breaks the format
	 */
	public static @NotNull Read read(@NotNull String context, @NotNull String text) {
		try {
			return new VanillaBlockReader(context, new BufferedReader(new StringReader(text))).read();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private Read read() throws IOException {
		String minecraft = single(next(), "minecraft");
		int protocol = Integer.parseInt(single(next(), "protocol"));
		int stateCount = Integer.parseInt(single(next(), "states"));
		int shapeCount = Integer.parseInt(single(next(), "shapes"));
		List<CollisionShape> shapes = new ArrayList<>(shapeCount);
		for (int index = 0; index < shapeCount; index++)
			shapes.add(shape(index, next()));

		List<BlockType> blocks = new ArrayList<>();
		List<BlockState> states = new ArrayList<>(stateCount);
		for (String[] line = next(); line != null; line = next()) {
			if (!line[0].equals("block"))
				throw failure("expected a block, found " + line[0]);
			block(blocks.size(), single(line, "block"), shapes, blocks, states);
		}
		if (states.size() != stateCount)
			throw failure("the blocks have " + states.size() + " states, the header " + stateCount);

		return new Read(minecraft, protocol, Collections.unmodifiableList(blocks), Collections.unmodifiableList(states));
	}

	private CollisionShape shape(int index, String[] line) {
		if (line == null || !line[0].equals("shape") || line.length < 2 || Integer.parseInt(line[1]) != index || (line.length - 2) % 6 != 0)
			throw failure("expected shape " + index + " with six coordinates per box");
		if (line.length == 2)
			return CollisionShape.EMPTY;

		List<CollisionBox> boxes = new ArrayList<>();
		for (int at = 2; at < line.length; at += 6)
			boxes.add(new CollisionBox(number(line[at]), number(line[at + 1]), number(line[at + 2]), number(line[at + 3]),
					number(line[at + 4]), number(line[at + 5])));
		return new CollisionShape(List.copyOf(boxes));
	}

	private void block(int id, String name, List<CollisionShape> shapes, List<BlockType> blocks, List<BlockState> states) throws IOException {
		Map<String, String[]> fields = new LinkedHashMap<>();
		List<String[]> properties = new ArrayList<>();
		List<String[]> cases = new ArrayList<>();
		for (String[] line = next(); ; line = next()) {
			if (line == null)
				throw failure(name + " has no end");
			if (line[0].equals("end"))
				break;
			if (line[0].equals("property"))
				properties.add(line);
			else if (line[0].equals("case"))
				cases.add(line);
			else if (!FIELDS.contains(line[0]))
				throw failure(name + " has an unknown line " + line[0]);
			else if (fields.put(line[0], line) != null)
				throw failure(name + " repeats " + line[0]);
		}

		String[] range = required(fields, "states", name, 4);
		int first = Integer.parseInt(range[1]);
		int count = Integer.parseInt(range[2]);
		BlockType.BlockTypeBuilder type = BlockType.builder()
				.id(id)
				.name(name)
				.minStateId(first)
				.maxStateId(first + count - 1)
				.defaultStateId(Integer.parseInt(range[3]))
				.kind(BlockKind.valueOf(single(required(fields, "kind", name, 2), "kind")))
				.collision(CollisionKind.valueOf(single(required(fields, "collision", name, 2), "collision")))
				.movement(movement(name, fields));
		for (String[] property : properties) {
			if (property.length < 3)
				throw failure(name + " has a property without values");
			type.property(new BlockProperty(property[1], List.of(Arrays.copyOfRange(property, 2, property.length))));
		}
		String[] offset = fields.get("offset");
		if (offset != null) {
			if (offset.length != 4 || !(offset[1].equals("XZ") || offset[1].equals("XYZ")))
				throw failure(name + " has a malformed offset");
			type.offset(new BlockOffset(offset[1].equals("XYZ"), number(offset[2]), number(offset[3])));
		}
		String[] tags = fields.get("tags");
		if (tags != null)
			for (int index = 1; index < tags.length; index++)
				type.tag(tags[index]);
		BlockType block = type.build();
		if (block.stateCount() != count || first != states.size())
			throw failure(name + " numbers its states from " + first + " with " + count + " states, expected from " + states.size()
					+ " with " + block.stateCount());

		List<String> collisions = repeated(required(fields, "collisions", name), count, name);
		List<String> fluids = fields.containsKey("fluids") ? repeated(fields.get("fluids"), count, name) : Collections.nCopies(count, "none");
		List<String> flows = repeated(required(fields, "flow", name), count, name);
		List<String> sturdy = repeated(required(fields, "sturdy", name), count, name);
		List<Map<CollisionCase, CollisionShape>> stateCases = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
			stateCases.add(new LinkedHashMap<>());
		for (String[] line : cases) {
			if (line.length != 7)
				throw failure(name + " has a malformed case");
			int state = Integer.parseInt(line[1]) - first;
			if (state < 0 || state >= count)
				throw failure(name + " has a case of state " + line[1] + ", which is not its own");
			CollisionCase context = new CollisionCase(EntityBottom.valueOf(line[2]), bool(line[3]), bool(line[4]), bool(line[5]));
			if (stateCases.get(state).put(context, shape(shapes, line[6], name)) != null)
				throw failure(name + " repeats a case of state " + line[1]);
		}

		blocks.add(block);
		for (int index = 0; index < count; index++) {
			int stateId = first + index;
			String[] fluid = fluids.get(index).split(":");
			FluidType fluidType = fluid[0].equals("none") ? FluidType.NONE : FluidType.valueOf(fluid[0].toUpperCase(Locale.ROOT));
			int amount = fluidType == FluidType.NONE ? 0 : Integer.parseInt(fluid[1]);
			List<String> flags = Arrays.asList(fluid).subList(Math.min(2, fluid.length), fluid.length);
			states.add(new BlockState(stateId, block, block.propertiesAt(stateId), shape(shapes, collisions.get(index), name), fluidType,
					Collections.unmodifiableMap(stateCases.get(index)), amount, flags.contains("source"), flags.contains("falling"),
					flow(flows.get(index), name), faces(sturdy.get(index), name)));
		}
	}

	private BlockMovement movement(String name, Map<String, String[]> fields) {
		String[] movement = required(fields, "movement", name, 5);
		BlockMovement.BlockMovementBuilder builder = BlockMovement.builder()
				.friction(Float.parseFloat(movement[1]))
				.speedFactor(Float.parseFloat(movement[2]))
				.jumpFactor(Float.parseFloat(movement[3]))
				.bounceFactor(number(movement[4]));
		String[] stuck = fields.get("stuck");
		if (stuck != null) {
			if (stuck.length != 3)
				throw failure(name + " has a malformed stuck line");
			builder.stuckSpeed(new StuckSpeed(number(stuck[1]), number(stuck[2])));
		}
		String[] behaviours = fields.get("behaviours");
		if (behaviours != null)
			for (int index = 1; index < behaviours.length; index++)
				builder.behaviour(MovementBehaviour.valueOf(behaviours[index]));
		return builder.build();
	}

	private boolean flow(String token, String name) {
		if (token.equals("blocks"))
			return true;
		if (token.equals("passes"))
			return false;
		throw failure(name + " has the flow " + token);
	}

	private Set<BlockFace> faces(String token, String name) {
		Set<BlockFace> faces = EnumSet.noneOf(BlockFace.class);
		if (token.equals("-"))
			return Collections.unmodifiableSet(faces);
		for (char initial : token.toCharArray()) {
			BlockFace face = Arrays.stream(BlockFace.values()).filter(candidate -> candidate.name().charAt(0) == initial).findFirst()
					.orElseThrow(() -> failure(name + " has the face " + initial));
			if (!faces.add(face))
				throw failure(name + " repeats the face " + initial);
		}
		return Collections.unmodifiableSet(faces);
	}

	private CollisionShape shape(List<CollisionShape> shapes, String index, String name) {
		int shape = Integer.parseInt(index);
		if (shape < 0 || shape >= shapes.size())
			throw failure(name + " uses shape " + shape + ", which the file does not have");
		return shapes.get(shape);
	}

	/**
	 * Expands a line of tokens with runs written {@code <token>*<count>} into one token per state.
	 */
	private List<String> repeated(String[] line, int count, String name) {
		List<String> tokens = new ArrayList<>(count);
		for (int index = 1; index < line.length; index++) {
			int star = line[index].lastIndexOf('*');
			if (star < 0) {
				tokens.add(line[index]);
				continue;
			}
			tokens.addAll(Collections.nCopies(Integer.parseInt(line[index].substring(star + 1)), line[index].substring(0, star)));
		}
		if (tokens.size() != count)
			throw failure(name + "'s " + line[0] + " line has " + tokens.size() + " entries for " + count + " states");
		return tokens;
	}

	private String[] required(Map<String, String[]> fields, String field, String name) {
		String[] line = fields.get(field);
		if (line == null)
			throw failure(name + " has no " + field + " line");
		return line;
	}

	private String[] required(Map<String, String[]> fields, String field, String name, int length) {
		String[] line = required(fields, field, name);
		if (line.length != length)
			throw failure(name + " has a malformed " + field + " line");
		return line;
	}

	private String single(String[] line, String keyword) {
		if (line == null || line.length != 2 || !line[0].equals(keyword))
			throw failure("expected " + keyword + " and its value");
		return line[1];
	}

	private static boolean bool(String token) {
		if (!token.equals("true") && !token.equals("false"))
			throw new IllegalStateException("Expected true or false, found " + token);
		return token.equals("true");
	}

	private static double number(String token) {
		return Double.parseDouble(token);
	}

	/**
	 * The next line that is not a comment, split at spaces, or {@code null} at the end.
	 */
	private String[] next() throws IOException {
		for (String line = lines.readLine(); line != null; line = lines.readLine()) {
			lineNumber++;
			if (!line.isEmpty() && line.charAt(0) != '#')
				return line.split(" ");
		}
		return null;
	}

	private IllegalStateException failure(String message) {
		return new IllegalStateException(context + " line " + lineNumber + ": " + message);
	}

	/**
	 * The blocks and states of one block data file.
	 */
	@Value
	public static class Read {
		/**
		 * The Minecraft version of the client the data was read from.
		 */
		@NotNull String minecraft;
		/**
		 * The protocol the client speaks.
		 */
		int protocol;
		/**
		 * The blocks, in block ID order.
		 */
		@NotNull List<BlockType> blocks;
		/**
		 * The states, in global state ID order.
		 */
		@NotNull List<BlockState> states;
	}
}
