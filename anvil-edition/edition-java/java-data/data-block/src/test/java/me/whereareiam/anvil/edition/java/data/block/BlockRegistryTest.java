package me.whereareiam.anvil.edition.java.data.block;

import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.BlockType;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.data.block.model.movement.BlockMovement;
import me.whereareiam.anvil.edition.java.data.block.model.movement.StuckSpeed;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.CollisionKind;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegistryTest {
	/**
	 * The state count and the default state of oak stairs of each protocol, as minecraft-data's {@code blocks.json}
	 * lists them.
	 */
	private static final Map<Integer, List<Integer>> PROTOCOLS = Map.of(
			754, List.of(17112, 1965),
			756, List.of(20342, 2021),
			758, List.of(20342, 2021),
			762, List.of(23725, 2881),
			766, List.of(26684, 2885),
			767, List.of(26684, 2885),
			774, List.of(29671, 3717),
			775, List.of(29873, 3918)
	);

	static Stream<Arguments> stairs() {
		return PROTOCOLS.entrySet().stream().map(entry -> Arguments.of(entry.getKey(), entry.getValue().get(0), entry.getValue().get(1)));
	}

	static Stream<Integer> protocols() {
		return PROTOCOLS.keySet().stream();
	}

	@ParameterizedTest
	@MethodSource("stairs")
	void countsTheStatesOfTheProtocol(int protocol, int states, int stairs) {
		BlockRegistry registry = registry(protocol);

		assertEquals(states, registry.stateCount());
		assertEquals(stairs, block(registry, "minecraft:oak_stairs").getDefaultStateId());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void airIsEmptyAndStoneAFullBlock(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertTrue(registry.isAir(0));
		assertTrue(registry.state(0).getCollision().isEmpty());
		assertTrue(registry.isAir(state(registry, "minecraft:cave_air").getId()));
		BlockState stone = registry.state(1);
		assertEquals("minecraft:stone", stone.getBlock().getName());
		assertTrue(stone.getCollision().isFullBlock());
		assertFalse(stone.isAir());
		assertEquals(BlockKind.OTHER, stone.getBlock().getKind());
		assertEquals(CollisionKind.FIXED, stone.getBlock().getCollision());
		assertEquals(0.6F, stone.getBlock().getMovement().getFriction());
		assertEquals(FluidType.NONE, stone.getFluid());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void aBottomSlabIsTheLowerHalf(int protocol) {
		BlockState slab = state(registry(protocol), "minecraft:oak_slab");

		assertEquals(Map.of("type", "bottom", "waterlogged", "false"), slab.getProperties());
		assertEquals(List.of(new CollisionBox(0, 0, 0, 1, 0.5, 1)), slab.getCollision().getBoxes());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void decodesStatePropertiesInVanillaOrder(int protocol) {
		BlockRegistry registry = registry(protocol);
		BlockType stairs = block(registry, "minecraft:oak_stairs");
		BlockState first = registry.state(stairs.getMinStateId());

		assertEquals(List.of("facing", "half", "shape", "waterlogged"), List.copyOf(first.getProperties().keySet()));
		assertEquals(Map.of("facing", "north", "half", "top", "shape", "straight", "waterlogged", "true"), first.getProperties());
		assertEquals(FluidType.WATER, first.getFluid());
		assertEquals(Map.of("facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "false"),
				registry.state(stairs.getDefaultStateId()).getProperties());
		assertEquals(80, registry.states(stairs).size());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void stairsCollideWithSeveralBoxes(int protocol) {
		BlockState stairs = state(registry(protocol), "minecraft:oak_stairs");

		assertTrue(stairs.getCollision().getBoxes().size() > 1, stairs.getCollision().toString());
		assertTrue(stairs.getCollision().getBoxes().stream().anyMatch(box -> box.getMinY() == 0 && box.getMaxY() == 0.5));
		assertTrue(stairs.getCollision().getBoxes().stream().anyMatch(box -> box.getMaxY() == 1));
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void aFenceIsOneAndAHalfBlocksHigh(int protocol) {
		BlockState post = state(registry(protocol), "minecraft:oak_fence");

		assertEquals(List.of(new CollisionBox(0.375, 0, 0.375, 0.625, 1.5, 0.625)), post.getCollision().getBoxes());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void carriesVanillaMovementFacts(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertEquals(0.98F, movement(registry, "minecraft:ice").getFriction());
		assertEquals(0.989F, movement(registry, "minecraft:blue_ice").getFriction());
		BlockMovement slime = movement(registry, "minecraft:slime_block");
		assertEquals(0.8F, slime.getFriction());
		assertEquals(1.0, slime.getBounceFactor());
		assertTrue(slime.has(MovementBehaviour.SLIME_STEP));
		BlockMovement honey = movement(registry, "minecraft:honey_block");
		assertEquals(0.4F, honey.getSpeedFactor());
		assertEquals(0.5F, honey.getJumpFactor());
		assertTrue(honey.has(MovementBehaviour.HONEY_SLIDE));
		assertEquals(0.4F, movement(registry, "minecraft:soul_sand").getSpeedFactor());
		assertEquals((double) 0.66F, movement(registry, "minecraft:red_bed").getBounceFactor());
		assertEquals(new StuckSpeed(0.25, (double) 0.05F), movement(registry, "minecraft:cobweb").getStuckSpeed());
		assertNull(movement(registry, "minecraft:stone").getStuckSpeed());
		assertTrue(movement(registry, "minecraft:ladder").has(MovementBehaviour.CLIMBABLE));
		assertTrue(movement(registry, "minecraft:scaffolding").has(MovementBehaviour.CLIMBABLE));
		assertFalse(movement(registry, "minecraft:stone").has(MovementBehaviour.CLIMBABLE));
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void knowsTheKindsVanillasMovementCodeAsksFor(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertEquals(BlockKind.AIR, block(registry, "minecraft:void_air").getKind());
		assertEquals(BlockKind.FENCE, block(registry, "minecraft:oak_fence").getKind());
		assertEquals(BlockKind.WALL, block(registry, "minecraft:cobblestone_wall").getKind());
		assertEquals(BlockKind.FENCE_GATE, block(registry, "minecraft:spruce_fence_gate").getKind());
		assertEquals(BlockKind.TRAPDOOR, block(registry, "minecraft:iron_trapdoor").getKind());
		assertEquals(BlockKind.LADDER, block(registry, "minecraft:ladder").getKind());
		assertEquals(BlockKind.SCAFFOLDING, block(registry, "minecraft:scaffolding").getKind());
		assertEquals(BlockKind.WATER, block(registry, "minecraft:water").getKind());
		assertEquals(BlockKind.OTHER, block(registry, "minecraft:kelp").getKind());
		assertEquals(BlockKind.ICE, block(registry, "minecraft:frosted_ice").getKind());
		assertEquals(BlockKind.OTHER, block(registry, "minecraft:packed_ice").getKind());
		assertEquals(BlockKind.MOVING_PISTON, block(registry, "minecraft:moving_piston").getKind());
		registry.block("minecraft:powder_snow")
				.ifPresent(powderSnow -> assertEquals(BlockKind.POWDER_SNOW, powderSnow.getKind()));
		assertEquals(CollisionKind.DYNAMIC, block(registry, "minecraft:scaffolding").getCollision());
		assertEquals(CollisionKind.OFFSET, block(registry, "minecraft:bamboo").getCollision());
		assertEquals(CollisionKind.FIXED, block(registry, "minecraft:oak_fence").getCollision());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void knowsTheFluidOfEachState(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertEquals(FluidType.WATER, state(registry, "minecraft:water").getFluid());
		assertEquals(FluidType.WATER, state(registry, "minecraft:kelp").getFluid());
		assertEquals(FluidType.LAVA, state(registry, "minecraft:lava").getFluid());
		assertTrue(state(registry, "minecraft:water").getCollision().isEmpty());
		BlockType slab = block(registry, "minecraft:oak_slab");
		assertEquals(FluidType.WATER, registry.states(slab).stream()
				.filter(state -> state.property("waterlogged").orElseThrow().equals("true")).findFirst().orElseThrow().getFluid());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void startsIntegerPropertiesAtTheirVanillaMinimum(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8"), block(registry, "minecraft:snow").getProperties().getFirst().getValues());
		assertEquals("1", state(registry, "minecraft:snow").property("layers").orElseThrow());
		assertEquals("0", state(registry, "minecraft:wheat").property("age").orElseThrow());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void refusesStatesOutsideTheRegistry(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertThrows(IllegalArgumentException.class, () -> registry.state(-1));
		assertThrows(IllegalArgumentException.class, () -> registry.state(registry.stateCount()));
		assertTrue(registry.block("minecraft:no_such_block").isEmpty());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void numbersBlocksAndStatesWithoutGaps(int protocol) {
		BlockRegistry registry = registry(protocol);

		int next = 0;
		for (int id = 0; id < registry.blocks().size(); id++) {
			BlockType block = registry.blocks().get(id);
			assertEquals(id, block.getId());
			assertEquals(next, block.getMinStateId());
			next = block.getMaxStateId() + 1;
		}
		assertEquals(registry.stateCount(), next);
	}

	private static @NotNull BlockRegistry registry(int protocol) {
		return JavaBlockData.shipped().require(protocol);
	}

	private static @NotNull BlockMovement movement(@NotNull BlockRegistry registry, @NotNull String name) {
		return block(registry, name).getMovement();
	}

	private static @NotNull BlockType block(@NotNull BlockRegistry registry, @NotNull String name) {
		return registry.block(name).orElseThrow(() -> new AssertionError(name + " is missing"));
	}

	private static @NotNull BlockState state(@NotNull BlockRegistry registry, @NotNull String name) {
		return registry.state(block(registry, name).getDefaultStateId());
	}
}
