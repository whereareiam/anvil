package me.whereareiam.anvil.protocol.java.api;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerInventoryLayoutTest {
	@Test
	void numbersTheHotbarMainInventoryArmorAndOffHand() {
		assertEquals(0, PlayerInventoryLayout.HOTBAR);
		assertEquals(9, PlayerInventoryLayout.MAIN);
		assertEquals(36, PlayerInventoryLayout.ARMOR);
		assertEquals(40, PlayerInventoryLayout.OFF_HAND);
		assertTrue(PlayerInventoryLayout.hotbar(0));
		assertTrue(PlayerInventoryLayout.hotbar(8));
		assertFalse(PlayerInventoryLayout.hotbar(-1));
		assertFalse(PlayerInventoryLayout.hotbar(9));
	}

	@Test
	void mapsEachInventoryIndexToTheSlotOfThePlayersInventoryContainer() {
		assertEquals(OptionalInt.of(36), PlayerInventoryLayout.containerSlot(0));
		assertEquals(OptionalInt.of(44), PlayerInventoryLayout.containerSlot(8));
		assertEquals(OptionalInt.of(9), PlayerInventoryLayout.containerSlot(9));
		assertEquals(OptionalInt.of(35), PlayerInventoryLayout.containerSlot(35));
		assertEquals(OptionalInt.of(8), PlayerInventoryLayout.containerSlot(36));
		assertEquals(OptionalInt.of(5), PlayerInventoryLayout.containerSlot(39));
		assertEquals(OptionalInt.of(45), PlayerInventoryLayout.containerSlot(40));
	}

	@Test
	void mapsNoIndexOutsideTheInventory() {
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.containerSlot(-1));
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.containerSlot(41));
		assertEquals(41, IntStream.rangeClosed(-5, 50).filter(index -> PlayerInventoryLayout.containerSlot(index).isPresent())
				.count());
	}
}
