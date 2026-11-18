package me.whereareiam.anvil.edition.java.data.block.generator;

import me.whereareiam.anvil.edition.java.data.block.generator.facts.BlockFact;
import me.whereareiam.anvil.edition.java.data.block.generator.facts.BlockFacts;
import me.whereareiam.anvil.edition.java.data.block.generator.source.MinecraftDataBlock;
import me.whereareiam.anvil.edition.java.data.block.generator.source.MinecraftDataProperty;
import me.whereareiam.anvil.edition.java.data.block.generator.source.MinecraftDataShapes;
import me.whereareiam.anvil.edition.java.data.block.generator.source.PropertyType;
import me.whereareiam.anvil.edition.java.data.block.generator.source.ShapeIndices;
import me.whereareiam.anvil.edition.java.data.block.model.BlockProperty;
import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.BlockType;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionShape;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds one protocol's blocks and states from its minecraft-data files and the block facts, checking the data as it
 * goes: block IDs are the blocks' positions, their state IDs run without gaps from {@code 0}, each block's state count
 * is the product of its properties' value counts, its default state is one of its states, and each state has a
 * collision shape. A state holds its block's fluid, or water where vanilla's {@code waterlogged} property is
 * {@code true}.
 */
public final class RegistryAssembly {
	private static final String NAMESPACE = "minecraft:";
	private static final String WATERLOGGED = "waterlogged";

	private final @NotNull String context;
	private final @NotNull BlockFacts facts;
	private final @NotNull Map<String, ShapeIndices> shapeIndices;
	private final @NotNull List<CollisionShape> shapes;
	private final @NotNull List<BlockType> blocks = new ArrayList<>();
	private final @NotNull List<BlockState> states = new ArrayList<>();
	private final @NotNull Set<String> names = new LinkedHashSet<>();

	private RegistryAssembly(@NotNull String context, @NotNull BlockFacts facts, @NotNull MinecraftDataShapes shapes) {
		this.context = context;
		this.facts = facts;
		this.shapeIndices = shapes.getBlocks();
		this.shapes = shapeTable(shapes.getShapes());
	}

	/**
	 * Assembles one protocol's registry.
	 *
	 * @param context what the data is, named in failures, such as {@code minecraft-data data/pc/1.16.2/blocks.json}
	 * @param blocks the blocks of {@code blocks.json}
	 * @param shapes {@code blockCollisionShapes.json}
	 * @param facts the block facts
	 * @return the blocks and states
	 * @throws IllegalStateException when the data breaks a rule above
	 */
	public static @NotNull Assembled assemble(
			@NotNull String context,
			@NotNull List<MinecraftDataBlock> blocks,
			@NotNull MinecraftDataShapes shapes,
			@NotNull BlockFacts facts
	) {
		if (blocks.isEmpty()) throw new IllegalStateException(context + " lists no blocks");

		RegistryAssembly assembly = new RegistryAssembly(context, facts, shapes);
		for (MinecraftDataBlock block : blocks)
			assembly.add(block);
		BlockState[] states = assembly.states.toArray(BlockState[]::new);
		return new Assembled(List.copyOf(assembly.blocks), states, Set.copyOf(assembly.names));
	}

	private void add(MinecraftDataBlock source) {
		String name = NAMESPACE + source.getName();
		if (source.getId() != blocks.size()) throw fail(name, "has id " + source.getId() + " at position " + blocks.size());
		if (!names.add(name)) throw fail(name, "is listed more than once");
		if (source.getMinStateId() != states.size() || source.getMaxStateId() < source.getMinStateId())
			throw fail(name, "has the states " + source.getMinStateId() + " to " + source.getMaxStateId()
					+ " after " + (states.size() - 1));
		if (source.getDefaultState() < source.getMinStateId() || source.getDefaultState() > source.getMaxStateId())
			throw fail(name, "has the default state " + source.getDefaultState() + " outside its states");

		BlockFact fact = facts.of(name);
		BlockType block = BlockType.builder()
				.id(source.getId())
				.name(name)
				.minStateId(source.getMinStateId())
				.maxStateId(source.getMaxStateId())
				.defaultStateId(source.getDefaultState())
				.properties(properties(name, source.getStates(), fact))
				.kind(fact.getKind())
				.collision(fact.getCollision())
				.movement(fact.getMovement())
				.build();
		blocks.add(block);
		addStates(block, source.getName(), fact.getFluid());
	}

	private List<BlockProperty> properties(String name, List<MinecraftDataProperty> sources, BlockFact fact) {
		List<BlockProperty> properties = new ArrayList<>();
		for (MinecraftDataProperty source : sources) {
			Integer start = fact.getPropertyStarts().get(source.getName());
			if (start != null && source.getType() == PropertyType.BOOL)
				throw fail(name, "has the boolean property " + source.getName() + ", which block-facts.toml gives a start");

			List<String> values = values(name, source, start);
			if (values.size() != source.getValueCount())
				throw fail(name, "gives " + source.getName() + " " + source.getValueCount() + " values, but lists "
						+ values.size());
			properties.add(new BlockProperty(source.getName(), values));
		}
		fact.getPropertyStarts().keySet().stream()
				.filter(property -> sources.stream().noneMatch(source -> source.getName().equals(property)))
				.findFirst()
				.ifPresent(property -> {
					throw fail(name, "has no property " + property + ", which block-facts.toml gives a start");
				});

		return properties;
	}

	private List<String> values(String name, MinecraftDataProperty source, Integer start) {
		try {
			return source.getType().values(source, start);
		} catch (IllegalStateException failure) {
			throw fail(name, failure.getMessage());
		}
	}

	private void addStates(BlockType block, String sourceName, FluidType fluid) {
		long combinations = block.getProperties().stream()
				.mapToLong(property -> property.getValues().size())
				.reduce(1, (product, count) -> product * count);
		if (combinations != block.stateCount())
			throw fail(block.getName(), "has " + block.stateCount() + " states, but its properties combine to " + combinations);

		List<Integer> indices = shapeIndices(block, sourceName);
		for (int offset = 0; offset < block.stateCount(); offset++) {
			int stateId = block.getMinStateId() + offset;
			Map<String, String> properties = block.propertiesAt(stateId);
			boolean waterlogged = Boolean.parseBoolean(properties.get(WATERLOGGED));
			FluidType stateFluid = fluid == FluidType.NONE && waterlogged ? FluidType.WATER : fluid;
			states.add(new BlockState(stateId, block, properties, shapes.get(indices.get(offset)), stateFluid));
		}
	}

	private List<Integer> shapeIndices(BlockType block, String sourceName) {
		ShapeIndices listed = shapeIndices.get(sourceName);
		if (listed == null) throw fail(block.getName(), "has no collision shape");

		try {
			List<Integer> indices = listed.forStates(block.stateCount());
			indices.stream().filter(index -> index < 0 || index >= shapes.size()).findFirst().ifPresent(index -> {
				throw new IllegalStateException("names collision shape " + index + ", which the table does not have");
			});
			return indices;
		} catch (IllegalStateException failure) {
			throw fail(block.getName(), failure.getMessage());
		}
	}

	private List<CollisionShape> shapeTable(Map<Integer, List<double[]>> table) {
		if (table.isEmpty()) throw new IllegalStateException(context + " has no collision shape table");

		List<CollisionShape> shapes = new ArrayList<>();
		for (int index = 0; index < table.size(); index++) {
			List<double[]> boxes = table.get(index);
			if (boxes == null) throw new IllegalStateException(context + " numbers its collision shapes with a gap at " + index);
			shapes.add(boxes.isEmpty() ? CollisionShape.EMPTY : new CollisionShape(boxes(index, boxes)));
		}

		return List.copyOf(shapes);
	}

	private List<CollisionBox> boxes(int shape, List<double[]> boxes) {
		List<CollisionBox> converted = new ArrayList<>();
		for (double[] box : boxes) {
			if (box.length != 6)
				throw new IllegalStateException(context + " has a box of shape " + shape + " that is not six numbers");
			converted.add(new CollisionBox(box[0], box[1], box[2], box[3], box[4], box[5]));
		}

		return List.copyOf(converted);
	}

	private IllegalStateException fail(String block, String problem) {
		return new IllegalStateException(context + ": " + block + " " + problem);
	}

	/**
	 * One protocol's assembled registry.
	 *
	 * @param blocks the blocks in ID order
	 * @param states the states in global ID order
	 * @param names the namespaced names of the blocks
	 */
	public record Assembled(
			@NotNull List<BlockType> blocks,
			@NotNull BlockState @NotNull [] states,
			@NotNull Set<String> names
	) {
	}
}
