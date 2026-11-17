package me.whereareiam.anvil.testkit.physics.harness.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads every block of a client through its family's {@link BlockProbe} and checks what Anvil's block data relies on
 * as it goes, failing rather than writing data that would not hold:
 * <ul>
 *     <li>blocks are numbered from {@code 0} in registry order and their states continue without gaps, each block's
 *     states in the order of its properties' values with the last property varying fastest, so a state's properties
 *     follow from its ID;</li>
 *     <li>a state's collision shape for a player standing on it is the same at every position, unless the block's
 *     shapes move with their position, whose unmoved shape is read back exactly;</li>
 *     <li>every state of a block makes an entity stuck the same way.</li>
 * </ul>
 */
public final class BlockTableReader<B, S> {
	/**
	 * How far from the origin, in blocks, the reader looks for block columns whose offset hash is at its extremes.
	 */
	private static final int SEARCH = 4096;

	private final BlockProbe<B, S> probe;
	private final ShapeProbe<B, S> shapes;
	private final MovementProbe<B, S> movement;
	/**
	 * Two block columns whose offset hash puts every axis at its lowest extreme.
	 */
	private final int[][] columns;

	private BlockTableReader(BlockProbe<B, S> probe) {
		this.probe = probe;
		this.shapes = probe.shapes();
		this.movement = probe.movement();
		this.columns = extremeColumns(shapes);
	}

	/**
	 * Reads the client's blocks.
	 *
	 * @param minecraft the Minecraft version the client reports
	 * @param probe     the family's probe of the bootstrapped client
	 * @return the client's blocks
	 * @throws IllegalStateException when the client's blocks break what Anvil's block data relies on
	 */
	public static <B, S> VanillaBlocks read(String minecraft, BlockProbe<B, S> probe) {
		return new BlockTableReader<>(probe).read(minecraft);
	}

	private VanillaBlocks read(String minecraft) {
		List<VanillaBlock> blocks = new ArrayList<>();
		int nextState = 0;
		for (B block : probe.blocks()) {
			String name = probe.name(block);
			if (probe.blockId(block) != blocks.size())
				throw new IllegalStateException(name + " has the block ID " + probe.blockId(block) + ", expected " + blocks.size());
			List<S> states = probe.states(block);
			List<VanillaProperty> properties = probe.properties(block);
			requireNumbered(name, nextState, states, properties);
			nextState += states.size();
			blocks.add(block(block, name, states, properties));
		}
		if (nextState != probe.stateCount())
			throw new IllegalStateException("The blocks number " + nextState + " states, the state registry holds " + probe.stateCount());

		return new VanillaBlocks(minecraft, probe.protocol(), Collections.unmodifiableList(blocks));
	}

	private VanillaBlock block(B block, String name, List<S> states, List<VanillaProperty> properties) {
		BlockOffset offset = shapes.offset(block) ? offset(name, states) : null;
		List<VanillaState> read = new ArrayList<>();
		boolean cases = false;
		for (S state : states) {
			VanillaState vanilla = state(name, state, offset);
			cases |= !vanilla.getCases().isEmpty();
			read.add(vanilla);
		}
		double[] stuck = stuck(name, states);

		return VanillaBlock.builder()
			.name(name)
			.defaultState(probe.stateId(probe.defaultState(block)))
			.properties(properties)
			.kind(movement.kind(block))
			.collision(collision(block, cases, offset))
			.offset(offset)
			.friction(movement.friction(block))
			.speedFactor(movement.speedFactor(block))
			.jumpFactor(movement.jumpFactor(block))
			// A block that stops the fall leaves a downward speed of -0.0, which is no bounce.
			.bounce(0.0 + movement.bounce(block))
			.stuck(stuck == null ? null : new double[] {stuck[0], stuck[1]})
			.behaviours(movement.behaviours(block))
			.tags(movementTags(movement.tags(block)))
			.states(Collections.unmodifiableList(read))
			.build();
	}

	private CollisionKind collision(B block, boolean cases, BlockOffset offset) {
		if (cases)
			return CollisionKind.DYNAMIC;
		if (offset != null)
			return CollisionKind.OFFSET;
		return shapes.dynamicShape(block) ? CollisionKind.DYNAMIC : CollisionKind.FIXED;
	}

	/**
	 * The speed factors every state of the block makes an entity stuck by, or {@code null} when none does; fails unless
	 * every state does the same, and the same east-west as north-south.
	 */
	private double[] stuck(String name, List<S> states) {
		double[] stuck = movement.stuck(states.get(0));
		for (S state : states) {
			double[] stuckHere = movement.stuck(state);
			if (!Arrays.equals(stuck, stuckHere))
				throw new IllegalStateException(name + " makes an entity stuck by " + Arrays.toString(stuck) + " in its first state, by "
					+ Arrays.toString(stuckHere) + " in " + probe.values(state));
		}
		if (stuck != null && stuck[0] != stuck[2])
			throw new IllegalStateException(name + " makes an entity stuck by " + Arrays.toString(stuck) + ", which differs east-west from north-south");
		return stuck;
	}

	private static List<String> movementTags(Set<String> tags) {
		List<String> kept = new ArrayList<>();
		for (String name : MovementTags.NAMES)
			if (tags.contains(name))
				kept.add(name);
		return Collections.unmodifiableList(kept);
	}

	/**
	 * Reads a state's collision shapes at the first extreme column for an offset block, else at the origin, and checks
	 * that a player standing on it meets the same shape at the second.
	 */
	private VanillaState state(String name, S state, BlockOffset offset) {
		boolean offsets = offset != null;
		int[] at = offsets ? new int[] {columns[0][0], 0, columns[0][1]} : new int[] {0, 0, 0};
		List<ShapeBox> standing = unmoved(name, state, at, CollisionCase.STANDING, offsets);
		int[] elsewhere = {columns[1][0], 64, columns[1][1]};
		List<ShapeBox> there = unmoved(name, state, elsewhere, CollisionCase.STANDING, offsets);
		if (!standing.equals(there))
			throw new IllegalStateException(name + " " + probe.values(state) + " has the collision shape " + standing + " at "
				+ Arrays.toString(at) + " and " + there + " at " + Arrays.toString(elsewhere)
				+ "; its shape depends on its position in a way the block data cannot record");

		Map<CollisionCase, List<ShapeBox>> cases = new LinkedHashMap<>();
		for (CollisionCase context : CollisionCase.cases()) {
			if (context.equals(CollisionCase.STANDING))
				continue;
			List<ShapeBox> shape = unmoved(name, state, at, context, offsets);
			if (!shape.equals(standing))
				cases.put(context, shape);
		}

		return new VanillaState(probe.stateId(state), standing, Collections.unmodifiableMap(cases), movement.fluid(state),
			movement.blocksFluidFlow(state), shapes.sturdyFaces(state));
	}

	/**
	 * The state's shape at a position, moved back by the block's offset there when it has one; fails unless moving it
	 * by the offset again gives the client's shape exactly.
	 */
	private List<ShapeBox> unmoved(String name, S state, int[] at, CollisionCase context, boolean offset) {
		List<ShapeBox> shape = shapes.collision(state, at[0], at[1], at[2], context);
		if (!offset)
			return shape;

		double[] by = shapes.offset(state, at[0], at[1], at[2]);
		List<ShapeBox> unmoved = new ArrayList<>(shape.size());
		for (ShapeBox box : shape) {
			ShapeBox back = box.move(-by[0], -by[1], -by[2]);
			if (!back.move(by[0], by[1], by[2]).equals(box))
				throw new IllegalStateException(name + "'s box " + box + " moved by " + Arrays.toString(by) + " cannot be read back exactly");
			unmoved.add(back);
		}
		return Collections.unmodifiableList(unmoved);
	}

	/**
	 * The block's offset at the first column whose hash puts every axis at its lowest extreme, where vanilla moves the
	 * shapes by the negative maximum on each axis it offsets.
	 */
	private BlockOffset offset(String name, List<S> states) {
		int[] column = columns[0];
		double[] offset = shapes.offset(states.get(0), column[0], 0, column[1]);
		for (S state : states)
			if (!Arrays.equals(offset, shapes.offset(state, column[0], 0, column[1])))
				throw new IllegalStateException(name + "'s states move by different offsets");
		if (offset[0] != offset[2] || offset[0] >= 0.0 || offset[1] > 0.0)
			throw new IllegalStateException(name + " moves by " + Arrays.toString(offset) + " where its offset should be at its lowest extreme");

		return new BlockOffset(offset[1] != 0.0, 0.0 - offset[0], 0.0 - offset[1]);
	}

	/**
	 * Two block columns whose offset seed has all three hashed nibbles at {@code 0}, the lowest extreme of every axis.
	 */
	private static int[][] extremeColumns(ShapeProbe<?, ?> shapes) {
		List<int[]> found = new ArrayList<>(2);
		for (int x = 0; x < SEARCH && found.size() < 2; x++)
			for (int z = 0; z < SEARCH && found.size() < 2; z++) {
				long seed = shapes.offsetSeed(x, z);
				if ((seed & 15L) == 0 && (seed >> 4 & 15L) == 0 && (seed >> 8 & 15L) == 0)
					found.add(new int[] {x, z});
			}
		if (found.size() < 2)
			throw new IllegalStateException("No two block columns within " + SEARCH + " blocks hash every offset to its extreme");

		return new int[][] {found.get(0), found.get(1)};
	}

	/**
	 * Checks that the block's states continue the global IDs without gaps, in the order of its properties' values with
	 * the last property varying fastest.
	 */
	private void requireNumbered(String name, int first, List<S> states, List<VanillaProperty> properties) {
		int expected = 1;
		for (VanillaProperty property : properties)
			expected *= property.getValues().size();
		if (states.size() != expected)
			throw new IllegalStateException(name + " has " + states.size() + " states for " + expected + " property combinations");

		for (int index = 0; index < states.size(); index++)
			requireValues(name, states.get(index), first + index, index, properties);
	}

	private void requireValues(String name, S state, int id, int index, List<VanillaProperty> properties) {
		if (probe.stateId(state) != id)
			throw new IllegalStateException(name + "'s state " + index + " has the ID " + probe.stateId(state) + ", expected " + id);

		Map<String, String> values = new LinkedHashMap<>();
		int remainder = index;
		for (int property = properties.size() - 1; property >= 0; property--) {
			List<String> choices = properties.get(property).getValues();
			values.put(properties.get(property).getName(), choices.get(remainder % choices.size()));
			remainder /= choices.size();
		}
		if (!values.equals(probe.values(state)))
			throw new IllegalStateException(name + "'s state " + id + " is " + probe.values(state) + ", expected " + values);
	}
}
