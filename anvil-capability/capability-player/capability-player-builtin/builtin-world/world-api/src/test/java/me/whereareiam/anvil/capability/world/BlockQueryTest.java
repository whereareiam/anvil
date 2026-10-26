package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.capability.world.exception.UnknownBlockNameException;
import me.whereareiam.anvil.capability.world.model.BlockIdentity;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockQueryTest {
	private static final SeenBlock STONE = new SeenBlock(0, 63, 0, 1,
			new BlockIdentity("minecraft:stone", Map.of(), false));
	private static final SeenBlock OPEN_DOOR = new SeenBlock(1, 64, 0, 4900,
			new BlockIdentity("minecraft:oak_door", Map.of("half", "lower", "open", "true"), false));
	private static final SeenBlock AIR = new SeenBlock(0, 64, 0, 0, new BlockIdentity("minecraft:air", Map.of(), true));
	private static final SeenBlock UNNAMED = new SeenBlock(0, 63, 0, 1, null);

	@Test
	void matchesANameWithOrWithoutItsNamespace() {
		assertTrue(BlockQuery.named("stone").test(STONE));
		assertTrue(BlockQuery.named("minecraft:oak_door").test(OPEN_DOOR));
		assertFalse(BlockQuery.named("stone").test(OPEN_DOOR));
		assertEquals("named minecraft:stone", BlockQuery.named("stone").toString());
	}

	@Test
	void matchesPropertiesAirAndStates() {
		assertTrue(BlockQuery.withProperty("open", "true").test(OPEN_DOOR));
		assertFalse(BlockQuery.withProperty("open", "false").test(OPEN_DOOR));
		assertFalse(BlockQuery.withProperty("open", "true").test(STONE));
		assertTrue(BlockQuery.air().test(AIR));
		assertFalse(BlockQuery.air().test(STONE));
		assertTrue(BlockQuery.state(1).test(STONE));
		assertTrue(BlockQuery.state(1).test(UNNAMED));
		assertTrue(BlockQuery.any().test(UNNAMED));
	}

	@Test
	void refusesToNameABlockKnownOnlyByItsState() {
		UnknownBlockNameException failure = assertThrows(UnknownBlockNameException.class,
				() -> BlockQuery.named("stone").test(UNNAMED));

		assertSame(UNNAMED, failure.getBlock());
		assertTrue(failure.getMessage().startsWith("The block at 0 63 0 is known only as state 1"));
		assertThrows(UnknownBlockNameException.class, () -> BlockQuery.air().test(UNNAMED));
		assertThrows(UnknownBlockNameException.class, () -> BlockQuery.withProperty("open", "true").test(UNNAMED));
	}

	@Test
	void describesCombinedQueries() {
		BlockQuery door = BlockQuery.named("oak_door").and(BlockQuery.withProperty("open", "true"));

		assertTrue(door.test(OPEN_DOOR));
		assertEquals("named minecraft:oak_door and with open=true", door.toString());
		assertEquals("(named minecraft:oak_door and with open=true) or a condition",
				door.or(block -> false).toString());
		assertEquals("not air", BlockQuery.air().negate().toString());
		assertTrue(BlockQuery.air().negate().test(STONE));
	}
}
