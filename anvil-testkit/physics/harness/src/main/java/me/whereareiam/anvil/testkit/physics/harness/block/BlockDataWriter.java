package me.whereareiam.anvil.testkit.physics.harness.block;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Writes a client's blocks as {@code <protocol>.blocks}, the block data {@code protocol-java-block}'s
 * {@code BlockDataGenerator} reads: UTF-8 text with Unix line endings, every float and double written by
 * {@link Float#toHexString(float)} and {@link Double#toHexString(double)}, which are exact and read back alike on every
 * Java release.
 *
 * <pre>{@code
 * # comment lines
 * minecraft 26.3
 * protocol 777
 * states <state count>
 * shapes <shape count>
 * shape <index> [<minX> <minY> <minZ> <maxX> <maxY> <maxZ>]...          one line per shape, numbered from 0
 * block <name>                                                          per block, in block ID order
 * states <first state ID> <state count> <default state ID>
 * property <name> <value>...                                            per property, in order
 * kind <BlockKind>
 * collision <CollisionKind>
 * offset <XZ|XYZ> <max horizontal> <max vertical>                       only for an offset block
 * movement <friction> <speed factor> <jump factor> <bounce>
 * stuck <horizontal> <vertical>                                         only for a block that makes entities stuck
 * behaviours <MovementBehaviour>...                                     only for a block with any
 * tags <tag>...                                                         the movement tags it is in, only when any
 * collisions <shape>[*<repeat>]...                                      each state's standing shape, in order
 * fluids <fluid>[*<repeat>]...                                          each state's fluid, in order
 * flow <blocks|passes>[*<repeat>]...                                    whether each state blocks fluid flow
 * sturdy <faces>[*<repeat>]...                                          each state's sturdy faces, such as DUNSWE or -
 * case <state ID> <EntityBottom> <descending> <falling> <leather boots> <shape>   per differing case
 * end
 * }</pre>
 * A fluid is {@code none} or {@code <water|lava>:<amount>}, followed by {@code :source} for a source and
 * {@code :falling} for a falling fluid.
 */
public final class BlockDataWriter {
	private BlockDataWriter() {
	}

	/**
	 * Writes the blocks into {@code directory} as {@code <protocol>.blocks}.
	 *
	 * @param directory the directory
	 * @param blocks    the client's blocks
	 * @return the written file
	 * @throws IOException when the file cannot be written
	 */
	public static Path write(Path directory, VanillaBlocks blocks) throws IOException {
		Map<List<ShapeBox>, Integer> shapes = shapes(blocks);
		int stateCount = 0;
		for (VanillaBlock block : blocks.getBlocks())
			stateCount += block.getStates().size();

		Files.createDirectories(directory);
		Path file = directory.resolve(blocks.getProtocol() + ".blocks");
		try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			line(writer, "# Block data of the vanilla client " + blocks.getMinecraft() + ", read from its own registries, classes and methods by");
			line(writer, "# the physics harness's generateVanillaData (anvil-testkit/physics/harness). Do not edit; generate it again.");
			line(writer, "minecraft " + blocks.getMinecraft());
			line(writer, "protocol " + blocks.getProtocol());
			line(writer, "states " + stateCount);
			line(writer, "shapes " + shapes.size());
			for (Map.Entry<List<ShapeBox>, Integer> shape : shapes.entrySet())
				line(writer, "shape " + shape.getValue() + boxes(shape.getKey()));
			for (VanillaBlock block : blocks.getBlocks())
				block(writer, block, shapes);
		}
		return file;
	}

	private static void block(BufferedWriter writer, VanillaBlock block, Map<List<ShapeBox>, Integer> shapes) throws IOException {
		List<VanillaState> states = block.getStates();
		line(writer, "block " + block.getName());
		line(writer, "states " + states.get(0).getId() + " " + states.size() + " " + block.getDefaultState());
		for (VanillaProperty property : block.getProperties())
			line(writer, "property " + property.getName() + " " + String.join(" ", property.getValues()));
		line(writer, "kind " + block.getKind());
		line(writer, "collision " + block.getCollision());
		BlockOffset offset = block.getOffset();
		if (offset != null)
			line(writer, "offset " + (offset.isVertical() ? "XYZ" : "XZ") + " " + Double.toHexString(offset.getMaxHorizontal()) + " "
				+ Double.toHexString(offset.getMaxVertical()));
		line(writer, "movement " + Float.toHexString(block.getFriction()) + " " + Float.toHexString(block.getSpeedFactor()) + " "
			+ Float.toHexString(block.getJumpFactor()) + " " + Double.toHexString(block.getBounce()));
		if (block.getStuck() != null)
			line(writer, "stuck " + Double.toHexString(block.getStuck()[0]) + " " + Double.toHexString(block.getStuck()[1]));
		if (!block.getBehaviours().isEmpty()) {
			List<String> names = new ArrayList<>();
			for (MovementBehaviour behaviour : MovementBehaviour.values())
				if (block.getBehaviours().contains(behaviour))
					names.add(behaviour.name());
			line(writer, "behaviours " + String.join(" ", names));
		}
		if (!block.getTags().isEmpty())
			line(writer, "tags " + String.join(" ", block.getTags()));
		line(writer, "collisions " + repeated(states, state -> String.valueOf(shapes.get(state.getCollision()))));
		boolean fluid = false;
		for (VanillaState state : states)
			fluid |= state.getFluid().getType() != VanillaFluid.Type.NONE;
		if (fluid)
			line(writer, "fluids " + repeated(states, state -> fluid(state.getFluid())));
		line(writer, "flow " + repeated(states, state -> state.isBlocksFluidFlow() ? "blocks" : "passes"));
		line(writer, "sturdy " + repeated(states, state -> faces(state.getSturdyFaces())));
		for (VanillaState state : states)
			for (Map.Entry<CollisionCase, List<ShapeBox>> entry : state.getCases().entrySet()) {
				CollisionCase context = entry.getKey();
				line(writer, "case " + state.getId() + " " + context.getBottom() + " " + context.isDescending() + " " + context.isFalling() + " "
					+ context.isLeatherBoots() + " " + shapes.get(entry.getValue()));
			}
		line(writer, "end");
	}

	/**
	 * Numbers every distinct shape in the order the blocks first use it, the empty shape first.
	 */
	private static Map<List<ShapeBox>, Integer> shapes(VanillaBlocks blocks) {
		Map<List<ShapeBox>, Integer> shapes = new LinkedHashMap<>();
		shapes.put(List.of(), 0);
		for (VanillaBlock block : blocks.getBlocks())
			for (VanillaState state : block.getStates()) {
				shapes.putIfAbsent(state.getCollision(), shapes.size());
				for (List<ShapeBox> shape : state.getCases().values())
					shapes.putIfAbsent(shape, shapes.size());
			}
		return shapes;
	}

	private static String boxes(List<ShapeBox> shape) {
		StringBuilder line = new StringBuilder();
		for (ShapeBox box : shape)
			line.append(' ').append(Double.toHexString(box.getMinX())).append(' ').append(Double.toHexString(box.getMinY()))
				.append(' ').append(Double.toHexString(box.getMinZ())).append(' ').append(Double.toHexString(box.getMaxX()))
				.append(' ').append(Double.toHexString(box.getMaxY())).append(' ').append(Double.toHexString(box.getMaxZ()));
		return line.toString();
	}

	/**
	 * The faces as their initials in {@link BlockFace} order, {@code -} for none.
	 */
	private static String faces(Set<BlockFace> faces) {
		StringBuilder initials = new StringBuilder();
		for (BlockFace face : BlockFace.values())
			if (faces.contains(face))
				initials.append(face.name().charAt(0));
		return initials.length() == 0 ? "-" : initials.toString();
	}

	private static String fluid(VanillaFluid fluid) {
		if (fluid.getType() == VanillaFluid.Type.NONE)
			return "none";
		return fluid.getType().name().toLowerCase(Locale.ROOT) + ":" + fluid.getAmount() + (fluid.isSource() ? ":source" : "")
			+ (fluid.isFalling() ? ":falling" : "");
	}

	/**
	 * Writes each state's token, runs of the same token as {@code <token>*<count>}.
	 */
	private static String repeated(List<VanillaState> states, Function<VanillaState, String> token) {
		List<String> runs = new ArrayList<>();
		String current = null;
		int count = 0;
		for (VanillaState state : states) {
			String next = token.apply(state);
			if (next.equals(current)) {
				count++;
				continue;
			}
			if (current != null)
				runs.add(count == 1 ? current : current + "*" + count);
			current = next;
			count = 1;
		}
		runs.add(count == 1 ? current : current + "*" + count);
		return String.join(" ", runs);
	}

	private static void line(BufferedWriter writer, String line) throws IOException {
		writer.write(line);
		writer.write('\n');
	}
}
