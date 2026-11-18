package me.whereareiam.anvil.edition.java.data.item;

import me.whereareiam.anvil.edition.java.data.item.exception.ItemDataUnavailableException;
import me.whereareiam.anvil.edition.java.data.item.model.ItemDataSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaItemDataTest {
	private static final JavaItemData DATA = JavaItemData.shipped();

	@Test
	void shipsItemDataForEveryProtocolMinecraftDataHas() {
		assertEquals(Set.of(754, 756, 758, 762, 766, 767, 774, 775), DATA.protocols());
	}

	@Test
	void readsEveryShippedProtocol() {
		for (int protocol : DATA.protocols()) {
			ItemRegistry registry = DATA.require(protocol);

			assertEquals(protocol, registry.protocol());
			assertEquals(Optional.of("minecraft:air"), registry.key(0), "Protocol " + protocol + " starts with no air");
			assertTrue(registry.id("minecraft:diamond").isPresent(), "Protocol " + protocol + " has no diamond");
		}
	}

	@Test
	void namesItemsByIdAndFindsThemByKey() {
		ItemRegistry registry = DATA.require(774);

		assertEquals(Optional.of("minecraft:stone"), registry.key(1));
		assertEquals(OptionalInt.of(1), registry.id("minecraft:stone"));
		assertTrue(registry.key(-1).isEmpty());
		assertTrue(registry.key(registry.keys().size()).isEmpty());
		assertTrue(registry.id("minecraft:no_such_item").isEmpty());
	}

	@Test
	void numbersItemsDifferentlyInEachProtocol() {
		int old = DATA.require(754).id("minecraft:emerald").orElseThrow();
		int current = DATA.require(774).id("minecraft:emerald").orElseThrow();

		assertTrue(old != current, "Emerald kept ID " + old);
		assertEquals(Optional.of("minecraft:emerald"), DATA.require(754).key(old));
	}

	@Test
	void listsTheSupportedProtocolsMinecraftDataLacksAsMissing() {
		assertEquals(Set.of(776, 777), DATA.missingProtocols());
		assertTrue(DATA.find(776).isEmpty());
	}

	@Test
	void reportsWhyAMissingProtocolHasNoItemData() {
		ItemDataUnavailableException failure = assertThrows(ItemDataUnavailableException.class, () -> DATA.require(777));

		assertEquals(777, failure.getProtocol());
		assertEquals("No item data for protocol 777: PrismarineJS/minecraft-data 3.117.0 has no item file for it",
				failure.getMessage());
	}

	@Test
	void refusesAProtocolAnvilDoesNotSupport() {
		ItemDataUnavailableException failure = assertThrows(ItemDataUnavailableException.class, () -> DATA.require(340));

		assertTrue(failure.getMessage().startsWith("No item data for protocol 340: Anvil supports no such protocol"),
				failure.getMessage());
	}

	@Test
	void readsEachProtocolOnce() {
		assertSame(DATA.require(775), DATA.require(775));
	}

	@Test
	void namesTheSourceOfTheData() {
		assertEquals(ItemDataSource.builder()
				.repository("PrismarineJS/minecraft-data")
				.version("3.117.0")
				.commit("15411f050f040e4c5df09352a75a9912ad8c3eeb")
				.items("data/pc/1.16.2/items.json")
				.build(), DATA.require(754).source());
	}
}
