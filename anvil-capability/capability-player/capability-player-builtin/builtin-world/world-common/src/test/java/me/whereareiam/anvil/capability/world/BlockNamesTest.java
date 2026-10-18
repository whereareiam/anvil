package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import me.whereareiam.anvil.protocol.java.data.BlockRegistry;
import me.whereareiam.anvil.protocol.java.data.JavaBlockData;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockNamesTest {
	private static final BlockRegistry REGISTRY = JavaBlockData.shipped().require(MinecraftVersion.parse("1.21.11"));

	@Test
	void namesTheStatesOfTheRelease() {
		BlockNames names = new BlockNames(REGISTRY);
		int door = REGISTRY.block("minecraft:oak_door").orElseThrow().getDefaultStateId();

		SeenBlock stone = names.block(1, 2, 3, 1);
		SeenBlock air = names.block(1, 3, 3, 0);
		SeenBlock closed = names.block(1, 4, 3, door);

		assertTrue(names.known());
		assertEquals("minecraft:stone", stone.name());
		assertEquals(Map.of(), stone.properties());
		assertTrue(air.isAir());
		assertEquals("minecraft:oak_door", closed.name());
		assertEquals(Optional.of("false"), closed.property("open"));
		assertEquals(3, closed.getZ());
	}

	@Test
	void keepsStatesUnnamedWithoutBlockData() {
		BlockNames names = new BlockNames(null);
		SeenBlock block = names.block(1, 2, 3, 1);

		assertFalse(names.known());
		assertFalse(block.isNamed());
		assertEquals(1, block.getStateId());
	}

	@Test
	void refusesAStateTheReleaseDoesNotHave() {
		BlockNames names = new BlockNames(REGISTRY);

		assertThrows(IllegalArgumentException.class, () -> names.block(0, 0, 0, REGISTRY.stateCount()));
	}
}
