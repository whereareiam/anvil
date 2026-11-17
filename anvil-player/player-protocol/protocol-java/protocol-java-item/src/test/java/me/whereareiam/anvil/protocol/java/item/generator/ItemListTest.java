package me.whereareiam.anvil.protocol.java.item.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemListTest {
	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void keysEachItemByIdInTheMinecraftNamespace() throws Exception {
		List<String> keys = ItemList.keys("test", JSON.readTree("""
				[{"id": 1, "name": "stone"}, {"id": 0, "name": "air", "displayName": "Air"}]
				"""));

		assertEquals(List.of("minecraft:air", "minecraft:stone"), keys);
	}

	@Test
	void startsWithAirWhenTheFileLeavesItOut() throws Exception {
		List<String> keys = ItemList.keys("test", JSON.readTree("[{\"id\": 1, \"name\": \"stone\"}]"));

		assertEquals(List.of("minecraft:air", "minecraft:stone"), keys);
	}

	@Test
	void refusesAGapInTheIds() throws Exception {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ItemList.keys("test",
				JSON.readTree("[{\"id\": 0, \"name\": \"air\"}, {\"id\": 2, \"name\": \"granite\"}]")));

		assertEquals("test has no item with ID 1", failure.getMessage());
	}

	@Test
	void refusesAnIdListedTwice() throws Exception {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ItemList.keys("test",
				JSON.readTree("[{\"id\": 0, \"name\": \"air\"}, {\"id\": 0, \"name\": \"stone\"}]")));

		assertEquals("test lists item ID 0 twice", failure.getMessage());
	}

	@Test
	void refusesAFileWithoutItems() throws Exception {
		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> ItemList.keys("test", JSON.readTree("[]")));

		assertEquals("test lists no items", failure.getMessage());
	}
}
