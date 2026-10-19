package me.whereareiam.anvil.protocol.java.data;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.data.model.BlockState;
import me.whereareiam.anvil.protocol.java.data.model.BlockType;
import me.whereareiam.anvil.protocol.java.data.model.CollisionBox;
import me.whereareiam.anvil.protocol.java.data.model.CollisionShape;
import me.whereareiam.anvil.protocol.java.data.type.BlockTrait;
import me.whereareiam.anvil.protocol.java.data.type.FluidType;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegistryReaderTest {
	private static final String DATA = """
			anvil-block-data 1
			source demo/data 1.0.0 abc data/blocks.json data/shapes.json
			minecraft 1.20.5,1.20.6
			states 7
			shapes 2
			-
			0.0 0.0 0.0 1.0 0.5 1.0 / 0.0 0.5 0.0 0.5 1.0 1.0
			blocks 2
			block minecraft:air 0 0 0 0.6 1.0 1.0 0.0 - - NONE AIR 0
			collision 0
			block minecraft:step 1 6 4 0.98 0.4 0.5 0.6600000262260437 0.25 0.05000000074505806 NONE HONEY_SLIDE,SLIME_STEP 2
			property half top,bottom,middle
			property waterlogged true,false
			collision 1 1 0 0 1 1
			""";

	@Test
	void readsBlocksStatesAndShapes() throws IOException {
		BlockRegistry registry = read(DATA);

		assertEquals(List.of(MinecraftVersion.parse("1.20.5"), MinecraftVersion.parse("1.20.6")), registry.minecraftVersions());
		assertEquals("demo/data 1.0.0 abc data/blocks.json data/shapes.json", registry.source());
		assertEquals(7, registry.stateCount());
		assertTrue(registry.isAir(0));
		assertSame(CollisionShape.EMPTY, registry.state(0).getCollision());

		BlockType step = registry.block("minecraft:step").orElseThrow();
		assertEquals(1, step.getId());
		assertEquals(0.98F, step.getFriction());
		assertEquals(0.4F, step.getSpeedFactor());
		assertEquals(0.5F, step.getJumpFactor());
		assertEquals((double) 0.66F, step.getBounceFactor());
		assertEquals((double) 0.05F, step.getStuckSpeed().getVertical());
		assertEquals(Set.of(BlockTrait.HONEY_SLIDE, BlockTrait.SLIME_STEP), step.getTraits());
	}

	@Test
	void variesTheLastPropertyFastest() throws IOException {
		BlockRegistry registry = read(DATA);

		List<BlockState> states = registry.states(registry.block("minecraft:step").orElseThrow());
		assertEquals(Map.of("half", "top", "waterlogged", "true"), states.get(0).getProperties());
		assertEquals(Map.of("half", "top", "waterlogged", "false"), states.get(1).getProperties());
		assertEquals(Map.of("half", "bottom", "waterlogged", "true"), states.get(2).getProperties());
		assertEquals(Map.of("half", "middle", "waterlogged", "false"), states.get(5).getProperties());
	}

	@Test
	void givesWaterloggedStatesWater() throws IOException {
		BlockRegistry registry = read(DATA);

		assertEquals(FluidType.WATER, registry.state(1).getFluid());
		assertEquals(FluidType.NONE, registry.state(2).getFluid());
	}

	@Test
	void sharesCollisionShapesBetweenStates() throws IOException {
		BlockRegistry registry = read(DATA);

		assertSame(registry.state(1).getCollision(), registry.state(5).getCollision());
		assertEquals(List.of(new CollisionBox(0, 0, 0, 1, 0.5, 1), new CollisionBox(0, 0.5, 0, 0.5, 1, 1)),
				registry.state(1).getCollision().getBoxes());
		assertTrue(registry.state(3).getCollision().isEmpty());
	}

	@Test
	void refusesAnotherFormat() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> read(DATA.replace("anvil-block-data 1", "anvil-block-data 2")));

		assertTrue(failure.getMessage().contains("does not start with 'anvil-block-data 1'"), failure.getMessage());
	}

	@Test
	void refusesACollisionLineThatDoesNotCoverTheStates() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> read(DATA.replace("collision 1 1 0 0 1 1", "collision 1 1")));

		assertTrue(failure.getMessage().contains("gives minecraft:step 2 collision shapes for 6 states"), failure.getMessage());
	}

	@Test
	void refusesStatesNoBlockHas() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> read(DATA.replace("states 7", "states 8")));

		assertTrue(failure.getMessage().contains("has no block for state 7"), failure.getMessage());
	}

	private static BlockRegistry read(String data) throws IOException {
		return new BlockRegistryReader(new BufferedReader(new StringReader(data))).read();
	}
}
