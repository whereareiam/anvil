package me.whereareiam.anvil.edition.java.data.block.generator;

import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.BlockType;
import me.whereareiam.anvil.edition.java.data.block.model.collision.BlockOffset;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionCase;
import me.whereareiam.anvil.edition.java.data.block.model.movement.StuckSpeed;
import me.whereareiam.anvil.edition.java.data.block.type.BlockFace;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.CollisionKind;
import me.whereareiam.anvil.edition.java.data.block.type.EntityBottom;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaBlockReaderTest {
	private static final String DATA = """
			# Block data of a vanilla client
			minecraft 1.21.11
			protocol 774
			states 3
			shapes 3
			shape 0
			shape 1 0x0.0p0 0x0.0p0 0x0.0p0 0x1.0p0 0x1.0p-1 0x1.0p0
			shape 2 0x0.0p0 0x0.0p0 0x0.0p0 0x1.0p0 0x1.0p-3 0x1.0p0
			block minecraft:air
			states 0 1 0
			kind AIR
			collision FIXED
			movement 0x1.333334p-1 0x1.0p0 0x1.0p0 0x0.0p0
			collisions 0
			flow passes
			sturdy -
			end
			block minecraft:sticky_slab
			states 1 2 2
			property waterlogged true false
			kind OTHER
			collision DYNAMIC
			offset XZ 0x1.0p-2 0x0.0p0
			movement 0x1.f5c29p-1 0x1.99999ap-2 0x1.0p-1 0x1.51eb86p-1
			stuck 0x1.0p-2 0x1.99999ap-5
			behaviours HONEY_SLIDE
			tags minecraft:climbable
			collisions 1*2
			fluids water:8:source none
			flow blocks*2
			sturdy DE D
			case 1 INSIDE true false false 2
			end
			""";

	@Test
	void readsBlocksAndStatesAsTheClientStatedThem() {
		VanillaBlockReader.Read read = VanillaBlockReader.read("774.blocks", DATA);

		assertEquals("1.21.11", read.getMinecraft());
		assertEquals(774, read.getProtocol());
		BlockType slab = read.getBlocks().get(1);
		assertEquals(BlockKind.OTHER, slab.getKind());
		assertEquals(CollisionKind.DYNAMIC, slab.getCollision());
		assertEquals(new BlockOffset(false, 0.25, 0.0), slab.getOffset());
		assertEquals(0.98F, slab.getMovement().getFriction());
		assertEquals(0.4F, slab.getMovement().getSpeedFactor());
		assertEquals((double) 0.66F, slab.getMovement().getBounceFactor());
		assertEquals(new StuckSpeed(0.25, (double) 0.05F), slab.getMovement().getStuckSpeed());
		assertTrue(slab.getMovement().has(MovementBehaviour.HONEY_SLIDE));
		assertTrue(slab.hasTag("minecraft:climbable"));

		BlockState wet = read.getStates().get(1);
		assertEquals(Map.of("waterlogged", "true"), wet.getProperties());
		assertEquals(List.of(new CollisionBox(0, 0, 0, 1, 0.5, 1)), wet.getCollision().getBoxes());
		assertEquals(List.of(new CollisionBox(0, 0, 0, 1, 0.125, 1)),
				wet.collision(new CollisionCase(EntityBottom.INSIDE, true, false, false)).getBoxes());
		assertEquals(wet.getCollision(), wet.collision(CollisionCase.STANDING));
		assertEquals(List.of(FluidType.WATER, 8, true, false), List.of(wet.getFluid(), wet.getFluidAmount(), wet.isFluidSource(),
				wet.isFluidFalling()));
		assertEquals(Set.of(BlockFace.DOWN, BlockFace.EAST), wet.getSturdyFaces());
		BlockState dry = read.getStates().get(2);
		assertEquals(FluidType.NONE, dry.getFluid());
		assertTrue(dry.isBlocksFluidFlow());
		assertTrue(dry.getCollisionCases().isEmpty());
	}

	@Test
	void refusesAnUnknownLine() {
		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> VanillaBlockReader.read("774.blocks", DATA.replace("kind AIR", "kind AIR\nbounce 1")));

		assertEquals("774.blocks line 12: minecraft:air has an unknown line bounce", failure.getMessage());
	}

	@Test
	void refusesALineWhoseEntriesDoNotMatchTheStates() {
		assertThrows(IllegalStateException.class, () -> VanillaBlockReader.read("774.blocks", DATA.replace("collisions 1*2", "collisions 1")));
	}

	@Test
	void refusesAStateCountTheBlocksDoNotHave() {
		assertThrows(IllegalStateException.class, () -> VanillaBlockReader.read("774.blocks", DATA.replace("states 3", "states 4")));
	}

	@Test
	void refusesAShapeTheFileDoesNotHave() {
		assertThrows(IllegalStateException.class,
				() -> VanillaBlockReader.read("774.blocks", DATA.replace("case 1 INSIDE true false false 2", "case 1 INSIDE true false false 3")));
	}
}
