package me.whereareiam.anvil.protocol.java.item;

import me.whereareiam.anvil.protocol.java.item.model.ItemDataSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemDataCodecTest {
	private static final ItemDataSource SOURCE = ItemDataSource.builder()
			.repository("PrismarineJS/minecraft-data")
			.version("3.117.0")
			.commit("c".repeat(40))
			.items("data/pc/1.21.11/items.json")
			.build();

	@Test
	void readsTheRegistryItWrote() throws IOException {
		ItemRegistry registry = new ItemRegistry(774, SOURCE, List.of("minecraft:air", "minecraft:stone"));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ItemDataCodec.writeRegistry(registry, bytes);

		ItemRegistry read = ItemDataCodec.readRegistry(new ByteArrayInputStream(bytes.toByteArray()));

		assertEquals(774, read.protocol());
		assertEquals(SOURCE, read.source());
		assertEquals(List.of("minecraft:air", "minecraft:stone"), read.keys());
	}

	@Test
	void readsTheIndexItWrote() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ItemDataCodec.writeIndex(new JavaItemData(Set.of(774, 754), Map.of(777, "no file")), bytes);

		JavaItemData read = ItemDataCodec.readIndex(new ByteArrayInputStream(bytes.toByteArray()));

		assertEquals(Set.of(754, 774), read.protocols());
		assertEquals(Set.of(777), read.missingProtocols());
		assertEquals("no file", read.missingReason(777));
	}

	@Test
	void refusesAKeyListedTwice() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new ItemRegistry(774, SOURCE, List.of("minecraft:air", "minecraft:air")));

		assertEquals("Protocol 774 names two items minecraft:air", failure.getMessage());
	}
}
