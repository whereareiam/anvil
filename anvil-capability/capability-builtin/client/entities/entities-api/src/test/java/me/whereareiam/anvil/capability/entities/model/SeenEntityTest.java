package me.whereareiam.anvil.capability.entities.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeenEntityTest {
	private static final SeenEntity STAND = SeenEntity.builder()
			.connection(1)
			.id(7)
			.uniqueId(new UUID(0, 7))
			.type("minecraft:armor_stand")
			.passengerIds(List.of())
			.build();

	@Test
	void labelsWithTheCustomNameBeforeTheDisplayText() {
		assertNull(STAND.label());
		assertEquals("hello", STAND.toBuilder().text("hello").build().label());
		assertEquals("Alice", STAND.toBuilder().customName("Alice").text("hello").build().label());
		assertTrue(STAND.toBuilder().customName("Alice").text("hello").build().labelContains("hell"));
		assertFalse(STAND.labelContains("hello"));
	}

	@Test
	void isTheSameEntityInAnotherStateOnlyWithItsConnectionIdAndUniqueId() {
		assertTrue(STAND.sameEntity(STAND.toBuilder().x(4).customName("moved").build()));
		assertFalse(STAND.sameEntity(STAND.toBuilder().connection(2).build()));
		assertFalse(STAND.sameEntity(STAND.toBuilder().id(8).build()));
		assertFalse(STAND.sameEntity(STAND.toBuilder().uniqueId(new UUID(0, 8)).build()));
	}
}
