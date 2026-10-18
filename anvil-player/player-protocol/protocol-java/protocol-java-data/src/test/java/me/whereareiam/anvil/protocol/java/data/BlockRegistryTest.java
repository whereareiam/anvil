package me.whereareiam.anvil.protocol.java.data;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.data.model.BlockState;
import me.whereareiam.anvil.protocol.java.data.model.BlockType;
import me.whereareiam.anvil.protocol.java.data.model.CollisionBox;
import me.whereareiam.anvil.protocol.java.data.model.StuckSpeed;
import me.whereareiam.anvil.protocol.java.data.type.BlockTrait;
import me.whereareiam.anvil.protocol.java.data.type.FluidType;
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
	 * The state count and the default state of oak stairs of each release, as minecraft-data's {@code blocks.json}
	 * lists them.
	 */
	private static final Map<String, List<Integer>> RELEASES = Map.of(
			"1.16.5", List.of(17112, 1965),
			"1.17.1", List.of(20342, 2021),
			"1.18.2", List.of(20342, 2021),
			"1.19.4", List.of(23725, 2881),
			"1.20.6", List.of(26684, 2885),
			"1.21.1", List.of(26684, 2885),
			"1.21.11", List.of(29671, 3717),
			"26.1.2", List.of(29873, 3918)
	);

	static Stream<Arguments> releases() {
		return RELEASES.entrySet().stream().map(release -> Arguments.of(release.getKey(), release.getValue().get(0), release.getValue().get(1)));
	}

	static Stream<String> versions() {
		return RELEASES.keySet().stream();
	}

	@ParameterizedTest
	@MethodSource("releases")
	void countsTheStatesOfTheRelease(String version, int states, int stairs) {
		BlockRegistry registry = registry(version);

		assertEquals(states, registry.stateCount());
		assertEquals(stairs, block(registry, "minecraft:oak_stairs").getDefaultStateId());
	}

	@ParameterizedTest
	@MethodSource("versions")
	void airIsEmptyAndStoneAFullBlock(String version) {
		BlockRegistry registry = registry(version);

		assertTrue(registry.isAir(0));
		assertTrue(registry.state(0).getCollision().isEmpty());
		assertTrue(registry.isAir(state(registry, "minecraft:cave_air").getId()));
		BlockState stone = registry.state(1);
		assertEquals("minecraft:stone", stone.getBlock().getName());
		assertTrue(stone.getCollision().isFullBlock());
		assertFalse(stone.isAir());
		assertEquals(0.6F, stone.getBlock().getFriction());
		assertEquals(FluidType.NONE, stone.getFluid());
	}

	@ParameterizedTest
	@MethodSource("versions")
	void aBottomSlabIsTheLowerHalf(String version) {
		BlockState slab = state(registry(version), "minecraft:oak_slab");

		assertEquals(Map.of("type", "bottom", "waterlogged", "false"), slab.getProperties());
		assertEquals(List.of(new CollisionBox(0, 0, 0, 1, 0.5, 1)), slab.getCollision().getBoxes());
	}

	@ParameterizedTest
	@MethodSource("versions")
	void decodesStatePropertiesInVanillaOrder(String version) {
		BlockRegistry registry = registry(version);
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
	@MethodSource("versions")
	void stairsCollideWithSeveralBoxes(String version) {
		BlockState stairs = state(registry(version), "minecraft:oak_stairs");

		assertTrue(stairs.getCollision().getBoxes().size() > 1, stairs.getCollision().toString());
		assertTrue(stairs.getCollision().getBoxes().stream().anyMatch(box -> box.getMinY() == 0 && box.getMaxY() == 0.5));
		assertTrue(stairs.getCollision().getBoxes().stream().anyMatch(box -> box.getMaxY() == 1));
	}

	@ParameterizedTest
	@MethodSource("versions")
	void aFenceIsOneAndAHalfBlocksHigh(String version) {
		BlockState post = state(registry(version), "minecraft:oak_fence");

		assertEquals(List.of(new CollisionBox(0.375, 0, 0.375, 0.625, 1.5, 0.625)), post.getCollision().getBoxes());
	}

	@ParameterizedTest
	@MethodSource("versions")
	void carriesVanillaMovementFacts(String version) {
		BlockRegistry registry = registry(version);

		assertEquals(0.98F, block(registry, "minecraft:ice").getFriction());
		assertEquals(0.989F, block(registry, "minecraft:blue_ice").getFriction());
		BlockType slime = block(registry, "minecraft:slime_block");
		assertEquals(0.8F, slime.getFriction());
		assertEquals(1.0, slime.getBounceFactor());
		assertTrue(slime.hasTrait(BlockTrait.SLIME_STEP));
		BlockType honey = block(registry, "minecraft:honey_block");
		assertEquals(0.4F, honey.getSpeedFactor());
		assertEquals(0.5F, honey.getJumpFactor());
		assertEquals(0.4F, block(registry, "minecraft:soul_sand").getSpeedFactor());
		assertEquals((double) 0.66F, block(registry, "minecraft:red_bed").getBounceFactor());
		assertEquals(new StuckSpeed(0.25, (double) 0.05F), block(registry, "minecraft:cobweb").getStuckSpeed());
		assertNull(block(registry, "minecraft:stone").getStuckSpeed());
		assertTrue(block(registry, "minecraft:ladder").hasTrait(BlockTrait.CLIMBABLE));
		assertTrue(block(registry, "minecraft:scaffolding").getTraits().containsAll(List.of(BlockTrait.CLIMBABLE, BlockTrait.DYNAMIC_COLLISION)));
		assertTrue(block(registry, "minecraft:bamboo").hasTrait(BlockTrait.OFFSET_COLLISION));
	}

	@ParameterizedTest
	@MethodSource("versions")
	void knowsTheFluidOfEachState(String version) {
		BlockRegistry registry = registry(version);

		assertEquals(FluidType.WATER, state(registry, "minecraft:water").getFluid());
		assertEquals(FluidType.WATER, state(registry, "minecraft:kelp").getFluid());
		assertEquals(FluidType.LAVA, state(registry, "minecraft:lava").getFluid());
		assertTrue(state(registry, "minecraft:water").getCollision().isEmpty());
		BlockType slab = block(registry, "minecraft:oak_slab");
		assertEquals(FluidType.WATER, registry.states(slab).stream()
				.filter(state -> state.property("waterlogged").orElseThrow().equals("true")).findFirst().orElseThrow().getFluid());
	}

	@ParameterizedTest
	@MethodSource("versions")
	void startsIntegerPropertiesAtTheirVanillaMinimum(String version) {
		BlockRegistry registry = registry(version);

		assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8"), block(registry, "minecraft:snow").getProperties().getFirst().getValues());
		assertEquals("1", state(registry, "minecraft:snow").property("layers").orElseThrow());
		assertEquals("0", state(registry, "minecraft:wheat").property("age").orElseThrow());
	}

	@ParameterizedTest
	@MethodSource("versions")
	void refusesStatesOutsideTheRegistry(String version) {
		BlockRegistry registry = registry(version);

		assertThrows(IllegalArgumentException.class, () -> registry.state(-1));
		assertThrows(IllegalArgumentException.class, () -> registry.state(registry.stateCount()));
		assertTrue(registry.block("minecraft:no_such_block").isEmpty());
	}

	@ParameterizedTest
	@MethodSource("versions")
	void numbersBlocksAndStatesWithoutGaps(String version) {
		BlockRegistry registry = registry(version);

		int next = 0;
		for (int id = 0; id < registry.blocks().size(); id++) {
			BlockType block = registry.blocks().get(id);
			assertEquals(id, block.getId());
			assertEquals(next, block.getMinStateId());
			next = block.getMaxStateId() + 1;
		}
		assertEquals(registry.stateCount(), next);
	}

	private static @NotNull BlockRegistry registry(@NotNull String version) {
		return JavaBlockData.shipped().require(MinecraftVersion.parse(version));
	}

	private static @NotNull BlockType block(@NotNull BlockRegistry registry, @NotNull String name) {
		return registry.block(name).orElseThrow(() -> new AssertionError(name + " is missing"));
	}

	private static @NotNull BlockState state(@NotNull BlockRegistry registry, @NotNull String name) {
		return registry.state(block(registry, name).getDefaultStateId());
	}
}
