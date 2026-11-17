package me.whereareiam.anvil.protocol.java.physics.world.shape;

import me.whereareiam.anvil.protocol.java.block.model.collision.CollisionBox;
import me.whereareiam.anvil.protocol.java.block.model.collision.CollisionShape;
import me.whereareiam.anvil.protocol.java.geometry.Box;
import me.whereareiam.anvil.protocol.java.geometry.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks how a shape splits into boxes and how high it reaches, as vanilla's {@code VoxelShape} does.
 */
class BlockShapeTest {
	private static final int[] BLOCK = {3, 64, -2};

	@Test
	void mergesARunOfCellsEastAndThenUpIntoOneBox() {
		BlockShape stair = shape(new CollisionBox(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
				new CollisionBox(0.0, 0.5, 0.5, 1.0, 1.0, 1.0));

		Box slab = new Box(new Vector(3.0, 64.0, -2.0), new Vector(4.0, 64.5, -1.0));
		Box step = new Box(new Vector(3.0, 64.5, -1.5), new Vector(4.0, 65.0, -1.0));
		assertEquals(List.of(slab, step), stair.toBoxes(BLOCK, true));
		assertEquals(1.0, stair.maxY());
	}

	@Test
	void walksEastWestFirstBefore1_18AndUpFirstFrom1_18() {
		BlockShape steps = shape(new CollisionBox(0.0, 0.5, 0.0, 0.5, 1.0, 1.0),
				new CollisionBox(0.5, 0.0, 0.0, 1.0, 0.5, 1.0));

		Box high = new Box(new Vector(3.0, 64.5, -2.0), new Vector(3.5, 65.0, -1.0));
		Box low = new Box(new Vector(3.5, 64.0, -2.0), new Vector(4.0, 64.5, -1.0));
		assertEquals(List.of(high, low), steps.toBoxes(BLOCK, false));
		assertEquals(List.of(low, high), steps.toBoxes(BLOCK, true));
	}

	@Test
	void anEmptyShapeHasNoBoxesAndNoTop() {
		BlockShape empty = BlockShape.of(CollisionShape.EMPTY);

		assertEquals(List.of(), empty.toBoxes(BLOCK, true));
		assertEquals(Double.NEGATIVE_INFINITY, empty.maxY());
	}

	private static BlockShape shape(CollisionBox... boxes) {
		return BlockShape.of(new CollisionShape(List.of(boxes)));
	}
}
