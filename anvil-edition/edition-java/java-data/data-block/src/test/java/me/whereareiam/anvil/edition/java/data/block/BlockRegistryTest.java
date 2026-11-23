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
		assertEquals(stairs, block(registry, "minecraft:oak_stairs").defaultStateId());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void airIsEmptyAndStoneAFullBlock(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertTrue(registry.isAir(0));
		assertTrue(registry.state(0).collision().isEmpty());
		assertTrue(registry.isAir(state(registry, "minecraft:cave_air").id()));
		BlockState stone = registry.state(1);
		assertEquals("minecraft:stone", stone.block().name());
		assertTrue(stone.collision().isFullBlock());
		assertFalse(stone.isAir());
		assertEquals(BlockKind.OTHER, stone.block().kind());
		assertEquals(CollisionKind.FIXED, stone.block().collision());
		assertEquals(0.6F, stone.block().movement().friction());
		assertEquals(FluidType.NONE, stone.fluid());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void aBottomSlabIsTheLowerHalf(int protocol) {
		BlockState slab = state(registry(protocol), "minecraft:oak_slab");

		assertEquals(Map.of("type", "bottom", "waterlogged", "false"), slab.properties());
		assertEquals(List.of(new CollisionBox(0, 0, 0, 1, 0.5, 1)), slab.collision().boxes());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void decodesStatePropertiesInVanillaOrder(int protocol) {
		BlockRegistry registry = registry(protocol);
		BlockType stairs = block(registry, "minecraft:oak_stairs");
		BlockState first = registry.state(stairs.minStateId());

		assertEquals(List.of("facing", "half", "shape", "waterlogged"), List.copyOf(first.properties().keySet()));
		assertEquals(Map.of("facing", "north", "half", "top", "shape", "straight", "waterlogged", "true"), first.properties());
		assertEquals(FluidType.WATER, first.fluid());
		assertEquals(Map.of("facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "false"),
				registry.state(stairs.defaultStateId()).properties());
		assertEquals(80, registry.states(stairs).size());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void stairsCollideWithSeveralBoxes(int protocol) {
		BlockState stairs = state(registry(protocol), "minecraft:oak_stairs");

		assertTrue(stairs.collision().boxes().size() > 1, stairs.collision().toString());
		assertTrue(stairs.collision().boxes().stream().anyMatch(box -> box.minY() == 0 && box.maxY() == 0.5));
		assertTrue(stairs.collision().boxes().stream().anyMatch(box -> box.maxY() == 1));
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void aFenceIsOneAndAHalfBlocksHigh(int protocol) {
		BlockState post = state(registry(protocol), "minecraft:oak_fence");

		assertEquals(List.of(new CollisionBox(0.375, 0, 0.375, 0.625, 1.5, 0.625)), post.collision().boxes());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void carriesVanillaMovementFacts(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertEquals(0.98F, movement(registry, "minecraft:ice").friction());
		assertEquals(0.989F, movement(registry, "minecraft:blue_ice").friction());
		BlockMovement slime = movement(registry, "minecraft:slime_block");
		assertEquals(0.8F, slime.friction());
		assertEquals(1.0, slime.bounceFactor());
		assertTrue(slime.has(MovementBehaviour.SLIME_STEP));
		BlockMovement honey = movement(registry, "minecraft:honey_block");
		assertEquals(0.4F, honey.speedFactor());
		assertEquals(0.5F, honey.jumpFactor());
		assertTrue(honey.has(MovementBehaviour.HONEY_SLIDE));
		assertEquals(0.4F, movement(registry, "minecraft:soul_sand").speedFactor());
		assertEquals((double) 0.66F, movement(registry, "minecraft:red_bed").bounceFactor());
		assertEquals(new StuckSpeed(0.25, (double) 0.05F), movement(registry, "minecraft:cobweb").stuckSpeed());
		assertNull(movement(registry, "minecraft:stone").stuckSpeed());
		assertTrue(movement(registry, "minecraft:ladder").has(MovementBehaviour.CLIMBABLE));
		assertTrue(movement(registry, "minecraft:scaffolding").has(MovementBehaviour.CLIMBABLE));
		assertFalse(movement(registry, "minecraft:stone").has(MovementBehaviour.CLIMBABLE));
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void knowsTheKindsVanillasMovementCodeAsksFor(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertEquals(BlockKind.AIR, block(registry, "minecraft:void_air").kind());
		assertEquals(BlockKind.FENCE, block(registry, "minecraft:oak_fence").kind());
		assertEquals(BlockKind.WALL, block(registry, "minecraft:cobblestone_wall").kind());
		assertEquals(BlockKind.FENCE_GATE, block(registry, "minecraft:spruce_fence_gate").kind());
		assertEquals(BlockKind.TRAPDOOR, block(registry, "minecraft:iron_trapdoor").kind());
		assertEquals(BlockKind.LADDER, block(registry, "minecraft:ladder").kind());
		assertEquals(BlockKind.SCAFFOLDING, block(registry, "minecraft:scaffolding").kind());
		assertEquals(BlockKind.WATER, block(registry, "minecraft:water").kind());
		assertEquals(BlockKind.OTHER, block(registry, "minecraft:kelp").kind());
		assertEquals(BlockKind.ICE, block(registry, "minecraft:frosted_ice").kind());
		assertEquals(BlockKind.OTHER, block(registry, "minecraft:packed_ice").kind());
		assertEquals(BlockKind.MOVING_PISTON, block(registry, "minecraft:moving_piston").kind());
		registry.block("minecraft:powder_snow")
				.ifPresent(powderSnow -> assertEquals(BlockKind.POWDER_SNOW, powderSnow.kind()));
		assertEquals(CollisionKind.DYNAMIC, block(registry, "minecraft:scaffolding").collision());
		assertEquals(CollisionKind.OFFSET, block(registry, "minecraft:bamboo").collision());
		assertEquals(CollisionKind.FIXED, block(registry, "minecraft:oak_fence").collision());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void knowsTheFluidOfEachState(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertEquals(FluidType.WATER, state(registry, "minecraft:water").fluid());
		assertEquals(FluidType.WATER, state(registry, "minecraft:kelp").fluid());
		assertEquals(FluidType.LAVA, state(registry, "minecraft:lava").fluid());
		assertTrue(state(registry, "minecraft:water").collision().isEmpty());
		BlockType slab = block(registry, "minecraft:oak_slab");
		assertEquals(FluidType.WATER, registry.states(slab).stream()
				.filter(state -> state.property("waterlogged").orElseThrow().equals("true")).findFirst().orElseThrow().fluid());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void startsIntegerPropertiesAtTheirVanillaMinimum(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertEquals(
				List.of("1", "2", "3", "4", "5", "6", "7", "8"),
				block(registry, "minecraft:snow").properties().getFirst().values()
		);
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
			assertEquals(id, block.id());
			assertEquals(next, block.minStateId());
			next = block.maxStateId() + 1;
		}
		assertEquals(registry.stateCount(), next);
	}

	private static @NotNull BlockRegistry registry(int protocol) {
		return JavaBlockData.shipped().require(protocol);
	}

	private static @NotNull BlockMovement movement(@NotNull BlockRegistry registry, @NotNull String name) {
		return block(registry, name).movement();
	}

	private static @NotNull BlockType block(@NotNull BlockRegistry registry, @NotNull String name) {
		return registry.block(name).orElseThrow(() -> new AssertionError(name + " is missing"));
	}

	private static @NotNull BlockState state(@NotNull BlockRegistry registry, @NotNull String name) {
		return registry.state(block(registry, name).defaultStateId());
	}
}
