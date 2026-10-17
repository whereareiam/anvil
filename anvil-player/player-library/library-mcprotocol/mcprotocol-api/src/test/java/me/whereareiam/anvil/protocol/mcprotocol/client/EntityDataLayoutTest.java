package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.protocol.mcprotocol.client.type.EntityDataField;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityDataLayoutTest {
	private final EntityDataLayout<String> layout = new EntityDataLayout<>(Map.of(
			"item", Map.of(8, EntityDataField.SHOWN_ITEM),
			"text_display", Map.of(23, EntityDataField.DISPLAY_TEXT)
	));

	@Test
	void findsTheValuesEveryEntityKeepsWhateverItsType() {
		for (String type : new String[]{"item", "zombie", null}) {
			assertEquals(EntityDataField.SHARED_FLAGS, layout.field(type, 0));
			assertEquals(EntityDataField.CUSTOM_NAME, layout.field(type, 2));
			assertEquals(EntityDataField.CUSTOM_NAME_VISIBLE, layout.field(type, 3));
			assertNull(layout.field(type, 1));
		}
	}

	@Test
	void findsTheValuesOfATypeOnlyForThatType() {
		assertEquals(EntityDataField.SHOWN_ITEM, layout.field("item", 8));
		assertEquals(EntityDataField.DISPLAY_TEXT, layout.field("text_display", 23));
		assertNull(layout.field("text_display", 8));
		assertNull(layout.field("zombie", 8));
		assertNull(layout.field(null, 8));
	}
}
