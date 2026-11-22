package me.whereareiam.anvil.edition.java.data.block;

import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.BlockType;
import me.whereareiam.anvil.edition.java.data.block.model.collision.BlockOffset;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionCase;
import me.whereareiam.anvil.edition.java.data.block.model.movement.BlockMovement;
import me.whereareiam.anvil.edition.java.data.block.model.movement.StuckSpeed;
import me.whereareiam.anvil.edition.java.data.block.type.BlockFace;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.CollisionKind;
import me.whereareiam.anvil.edition.java.data.block.type.EntityBottom;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegistryTest {
	/**
	 * The state count and the default state of oak stairs of each protocol, as each release's vanilla client numbers
	 * them.
	 */
	private static final Map<Integer, List<Integer>> PROTOCOLS = Map.of(
			754, List.of(17112, 1965),
			756, List.of(20342, 2021),
			758, List.of(20342, 2021),
			762, List.of(23725, 2881),
			766, List.of(26684, 2885),
			767, List.of(26684, 2885),
			774, List.of(29671, 3717),
			775, List.of(29873, 3918),
			776, List.of(32366, 3918),
			777, List.of(35723, 5474)
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
		// From 26.2 a bed states its bounce restitution as data, 0.75F; before, its bounceUp reverses 0.66F of the speed.
		assertEquals(protocol >= 776 ? (double) 0.75F : (double) 0.66F, movement(registry, "minecraft:red_bed").getBounceFactor());
		assertEquals(new StuckSpeed(0.25, (double) 0.05F), movement(registry, "minecraft:cobweb").getStuckSpeed());
		assertNull(movement(registry, "minecraft:stone").getStuckSpeed());
		assertTrue(block(registry, "minecraft:ladder").hasTag("minecraft:climbable"));
		assertTrue(block(registry, "minecraft:oak_fence").hasTag("minecraft:fences"));
		assertEquals(protocol >= 776, block(registry, "minecraft:honey_block").hasTag("minecraft:suppresses_bounce"));
		assertTrue(block(registry, "minecraft:scaffolding").hasTag("minecraft:climbable"));
		assertFalse(block(registry, "minecraft:stone").hasTag("minecraft:climbable"));
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void knowsTheKindsVanillasMovementCodeAsksFor(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertEquals(BlockKind.AIR, block(registry, "minecraft:void_air").getKind());
		assertEquals(BlockKind.OTHER, block(registry, "minecraft:oak_fence").getKind());
		assertTrue(block(registry, "minecraft:cobblestone_wall").hasTag("minecraft:walls"));
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
	void knowsTheLevelOfEachFluidState(int protocol) {
		BlockRegistry registry = registry(protocol);
		BlockType water = block(registry, "minecraft:water");
		BlockState source = registry.state(water.getMinStateId());
		BlockState flowing = registry.state(water.getMinStateId() + 1);
		BlockState falling = registry.state(water.getMinStateId() + 8);

		assertEquals(List.of(8, true, false), List.of(source.getFluidAmount(), source.isFluidSource(), source.isFluidFalling()));
		assertEquals(List.of(7, false, false), List.of(flowing.getFluidAmount(), flowing.isFluidSource(), flowing.isFluidFalling()));
		assertEquals(List.of(8, false, true), List.of(falling.getFluidAmount(), falling.isFluidSource(), falling.isFluidFalling()));
		assertEquals(0, state(registry, "minecraft:stone").getFluidAmount());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void knowsHowStatesMeetFluids(int protocol) {
		BlockRegistry registry = registry(protocol);

		assertTrue(state(registry, "minecraft:stone").isBlocksFluidFlow());
		assertFalse(state(registry, "minecraft:water").isBlocksFluidFlow());
		assertEquals(EnumSet.allOf(BlockFace.class), state(registry, "minecraft:stone").getSturdyFaces());
		assertEquals(Set.of(BlockFace.DOWN), state(registry, "minecraft:oak_slab").getSturdyFaces());
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void recordsWhatScaffoldingGivesEachPlayer(int protocol) {
		BlockRegistry registry = registry(protocol);
		BlockType scaffolding = block(registry, "minecraft:scaffolding");
		BlockState hangingBottom = registry.states(scaffolding).stream()
				.filter(state -> state.getProperties().equals(Map.of("bottom", "true", "distance", "1", "waterlogged", "false")))
				.findFirst().orElseThrow();

		assertFalse(hangingBottom.getCollision().isEmpty());
		assertEquals(List.of(new CollisionBox(0, 0, 0, 1, 0.125, 1)),
				hangingBottom.collision(new CollisionCase(EntityBottom.INSIDE, false, false, false)).getBoxes());
		assertTrue(hangingBottom.collision(new CollisionCase(EntityBottom.BELOW, false, false, false)).isEmpty());
		assertEquals(hangingBottom.getCollision(), hangingBottom.collision(CollisionCase.STANDING));
	}

	@ParameterizedTest
	@MethodSource("protocols")
	void movesBambooByItsOffset(int protocol) {
		BlockType bamboo = block(registry(protocol), "minecraft:bamboo");

		assertEquals(new BlockOffset(false, 0.25, 0.0), bamboo.getOffset());
		assertNull(block(registry(protocol), "minecraft:stone").getOffset());
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
