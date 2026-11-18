package me.whereareiam.anvil.edition.java.protocol;

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

	@Test
	void mapsEachSlotOfThePlayersInventoryContainerBackToItsIndex() {
		for (int index = 0; index <= PlayerInventoryLayout.OFF_HAND; index++)
			assertEquals(OptionalInt.of(index),
					PlayerInventoryLayout.inventoryIndex(PlayerInventoryLayout.containerSlot(index).orElseThrow()));
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.inventoryIndex(0));
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.inventoryIndex(4));
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.inventoryIndex(46));
	}

	@Test
	void mapsTheMainInventoryAndHotbarBelowAnOpenedContainersOwnSlots() {
		int chest = 27 + 36;

		assertEquals(OptionalInt.of(27), PlayerInventoryLayout.menuSlot(9, chest));
		assertEquals(OptionalInt.of(53), PlayerInventoryLayout.menuSlot(35, chest));
		assertEquals(OptionalInt.of(54), PlayerInventoryLayout.menuSlot(0, chest));
		assertEquals(OptionalInt.of(62), PlayerInventoryLayout.menuSlot(8, chest));
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.menuSlot(36, chest));
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.menuSlot(40, chest));
		for (int index = 0; index < PlayerInventoryLayout.ARMOR; index++)
			assertEquals(OptionalInt.of(index), PlayerInventoryLayout.menuInventoryIndex(
					PlayerInventoryLayout.menuSlot(index, chest).orElseThrow(), chest));
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.menuInventoryIndex(26, chest));
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.menuInventoryIndex(63, chest));
		assertEquals(OptionalInt.empty(), PlayerInventoryLayout.menuSlot(0, 9));
	}
}
