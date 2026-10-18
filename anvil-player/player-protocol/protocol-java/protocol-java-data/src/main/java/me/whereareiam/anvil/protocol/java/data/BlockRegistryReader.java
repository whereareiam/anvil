package me.whereareiam.anvil.protocol.java.data;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.data.model.BlockProperty;
import me.whereareiam.anvil.protocol.java.data.model.BlockState;
import me.whereareiam.anvil.protocol.java.data.model.BlockType;
import me.whereareiam.anvil.protocol.java.data.model.CollisionBox;
import me.whereareiam.anvil.protocol.java.data.model.CollisionShape;
import me.whereareiam.anvil.protocol.java.data.model.StuckSpeed;
import me.whereareiam.anvil.protocol.java.data.type.BlockTrait;
import me.whereareiam.anvil.protocol.java.data.type.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads one release's generated block data, in the line format the build's {@code BlockDataEncoder} writes.
 *
 * <p>A state's property values follow from its offset in its block, the last property varying fastest; its fluid is
 * the block's own, or water where its {@code waterlogged} property is {@code true}.</p>
 */
final class BlockRegistryReader {
	private static final String HEADER = "anvil-block-data 1";
	private static final String ABSENT = "-";

	private final @NotNull BufferedReader input;
	private int line;

	BlockRegistryReader(@NotNull BufferedReader input) {
		this.input = input;
	}

	/**
	 * Reads the whole data.
	 *
	 * @return the registry
	 * @throws IOException when the data cannot be read
	 * @throws IllegalStateException when the data is malformed
	 */
	@NotNull BlockRegistry read() throws IOException {
		if (!HEADER.equals(next())) throw malformed("does not start with '" + HEADER + "'");

		String source = field("source");
		List<MinecraftVersion> versions = Arrays.stream(field("minecraft").split(",")).map(MinecraftVersion::parse).toList();
		BlockState[] states = new BlockState[count("states")];
		List<CollisionShape> shapes = shapes(count("shapes"));
		int blockCount = count("blocks");
		List<BlockType> blocks = new ArrayList<>(blockCount);
		for (int id = 0; id < blockCount; id++)
			blocks.add(block(id, shapes, states));
		if (next() != null) throw malformed("has lines after its last block");

		for (int id = 0; id < states.length; id++)
			if (states[id] == null) throw malformed("has no block for state " + id);
		return new BlockRegistry(versions, source, blocks, states);
	}

	private @NotNull List<CollisionShape> shapes(int count) throws IOException {
		List<CollisionShape> shapes = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			String text = next();
			if (text == null) throw malformed("ends inside its shape table");
			if (text.equals(ABSENT)) {
				shapes.add(CollisionShape.EMPTY);
				continue;
			}

			List<CollisionBox> boxes = new ArrayList<>();
			for (String box : text.split(" / "))
				boxes.add(box(box));
			shapes.add(new CollisionShape(List.copyOf(boxes)));
		}

		return List.copyOf(shapes);
	}

	private @NotNull CollisionBox box(@NotNull String text) {
		String[] values = text.split(" ");
		if (values.length != 6) throw malformed("has a box without six coordinates");

		return new CollisionBox(
				Double.parseDouble(values[0]),
				Double.parseDouble(values[1]),
				Double.parseDouble(values[2]),
				Double.parseDouble(values[3]),
				Double.parseDouble(values[4]),
				Double.parseDouble(values[5])
		);
	}

	private @NotNull BlockType block(int id, @NotNull List<CollisionShape> shapes, @NotNull BlockState @NotNull [] states) throws IOException {
		String[] fields = field("block").split(" ");
		if (fields.length != 13) throw malformed("has a block line without its 13 fields");

		BlockType.BlockTypeBuilder builder = BlockType.builder()
				.id(id)
				.name(fields[0])
				.minStateId(Integer.parseInt(fields[1]))
				.maxStateId(Integer.parseInt(fields[2]))
				.defaultStateId(Integer.parseInt(fields[3]))
				.friction(Float.parseFloat(fields[4]))
				.speedFactor(Float.parseFloat(fields[5]))
				.jumpFactor(Float.parseFloat(fields[6]))
				.bounceFactor(Double.parseDouble(fields[7]))
				.stuckSpeed(stuckSpeed(fields[8], fields[9]));
		if (!fields[11].equals(ABSENT))
			for (String trait : fields[11].split(","))
				builder.trait(BlockTrait.valueOf(trait));
		int propertyCount = Integer.parseInt(fields[12]);
		for (int index = 0; index < propertyCount; index++) {
			String[] property = field("property").split(" ");
			if (property.length != 2) throw malformed("has a property line without a name and values");
			builder.property(new BlockProperty(property[0], List.of(property[1].split(","))));
		}

		BlockType block = builder.build();
		if (block.getMaxStateId() >= states.length || block.getMinStateId() < 0 || block.getMaxStateId() < block.getMinStateId())
			throw malformed("gives " + block.getName() + " states outside the registry");
		states(block, FluidType.valueOf(fields[10]), collisions(block, shapes), states);

		return block;
	}

	private @Nullable StuckSpeed stuckSpeed(@NotNull String horizontal, @NotNull String vertical) {
		if (horizontal.equals(ABSENT)) return null;

		return new StuckSpeed(Double.parseDouble(horizontal), Double.parseDouble(vertical));
	}

	private @NotNull CollisionShape @NotNull [] collisions(@NotNull BlockType block, @NotNull List<CollisionShape> shapes) throws IOException {
		String[] indices = field("collision").split(" ");
		int count = block.stateCount();
		if (indices.length != 1 && indices.length != count)
			throw malformed("gives " + block.getName() + " " + indices.length + " collision shapes for " + count + " states");

		CollisionShape[] collisions = new CollisionShape[count];
		for (int offset = 0; offset < count; offset++) {
			int index = Integer.parseInt(indices[indices.length == 1 ? 0 : offset]);
			if (index < 0 || index >= shapes.size()) throw malformed("names collision shape " + index + ", which it does not have");
			collisions[offset] = shapes.get(index);
		}

		return collisions;
	}

	private void states(
			@NotNull BlockType block,
			@NotNull FluidType fluid,
			@NotNull CollisionShape @NotNull [] collisions,
			@NotNull BlockState @NotNull [] states
	) {
		List<BlockProperty> properties = block.getProperties();
		for (int offset = 0; offset < block.stateCount(); offset++) {
			String[] chosen = new String[properties.size()];
			int remainder = offset;
			for (int index = properties.size() - 1; index >= 0; index--) {
				List<String> choices = properties.get(index).getValues();
				chosen[index] = choices.get(remainder % choices.size());
				remainder /= choices.size();
			}

			Map<String, String> values = new LinkedHashMap<>();
			for (int index = 0; index < chosen.length; index++)
				values.put(properties.get(index).getName(), chosen[index]);
			FluidType stateFluid = fluid == FluidType.NONE && "true".equals(values.get("waterlogged")) ? FluidType.WATER : fluid;
			states[block.getMinStateId() + offset] = new BlockState(
					block.getMinStateId() + offset,
					block,
					Collections.unmodifiableMap(values),
					collisions[offset],
					stateFluid
			);
		}
	}

	private @NotNull String field(@NotNull String name) throws IOException {
		String text = next();
		if (text == null || !text.startsWith(name + " ")) throw malformed("lacks its '" + name + "' line");

		return text.substring(name.length() + 1);
	}

	private int count(@NotNull String name) throws IOException {
		try {
			return Integer.parseInt(field(name));
		} catch (NumberFormatException exception) {
			throw malformed("has no count on its '" + name + "' line");
		}
	}

	private @Nullable String next() throws IOException {
		line++;
		return input.readLine();
	}

	private @NotNull IllegalStateException malformed(@NotNull String problem) {
		return new IllegalStateException("Block data line " + line + ": the data " + problem);
	}
}
