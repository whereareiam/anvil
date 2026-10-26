package me.whereareiam.anvil.capability.world.model;

import me.whereareiam.anvil.capability.world.exception.UnknownBlockNameException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeenBlockTest {
	@Test
	void tellsTheNameAndPropertiesOfANamedState() {
		Map<String, String> properties = new LinkedHashMap<>();
		properties.put("facing", "north");
		properties.put("half", "bottom");
		SeenBlock stairs = new SeenBlock(3, 64, -2, 2000, new BlockIdentity("minecraft:oak_stairs", properties, false));

		assertTrue(stairs.isNamed());
		assertEquals("minecraft:oak_stairs", stairs.name());
		assertEquals(Optional.of("north"), stairs.property("facing"));
		assertEquals(Optional.empty(), stairs.property("open"));
		assertEquals(properties, stairs.properties());
		assertFalse(stairs.isAir());
		assertEquals("minecraft:oak_stairs[facing=north,half=bottom] at 3 64 -2", stairs.describe());
	}

	@Test
	void keepsABlockOfAReleaseWithoutBlockDataKnownByItsStateOnly() {
		SeenBlock block = new SeenBlock(3, 64, -2, 2000, null);

		assertFalse(block.isNamed());
		assertEquals(Optional.empty(), block.identity());
		assertEquals(2000, block.getStateId());
		assertEquals("state 2000 at 3 64 -2", block.describe());
		assertThrows(UnknownBlockNameException.class, block::name);
		assertThrows(UnknownBlockNameException.class, block::isAir);
		assertThrows(UnknownBlockNameException.class, () -> block.property("facing"));
	}
}
