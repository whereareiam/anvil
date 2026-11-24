package me.whereareiam.anvil.edition.java.data.block;

import me.whereareiam.anvil.edition.java.data.block.exception.BlockDataUnavailableException;
import me.whereareiam.anvil.edition.java.data.block.model.BlockDataSource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaBlockDataTest {
	private static final JavaBlockData DATA = JavaBlockData.shipped();

	@Test
	void shipsBlockDataForEveryProtocolOfTheProtocolLibraries() {
		assertEquals(Set.of(754, 756, 758, 762, 766, 767, 774, 775, 776, 777), DATA.protocols());
		assertEquals(Set.of(), DATA.missingProtocols());
	}

	@Test
	void readsEveryShippedProtocol() {
		for (int protocol : DATA.protocols()) {
			BlockRegistry registry = DATA.require(protocol);

			assertEquals(protocol, registry.protocol());
			assertTrue(registry.stateCount() > 0, "Protocol " + protocol + " has no block states");
		}
	}

	@Test
	void refusesAProtocolAnvilDoesNotSupport() {
		BlockDataUnavailableException failure = assertThrows(BlockDataUnavailableException.class, () -> DATA.require(340));

		assertTrue(failure.getMessage().startsWith("No block data for protocol 340: Anvil supports no such protocol"),
				failure.getMessage());
		assertTrue(DATA.find(765).isEmpty());
	}

	@Test
	void readsEachProtocolOnce() {
		assertSame(DATA.require(775), DATA.require(775));
	}

	@Test
	void namesTheVanillaClientTheDataWasReadFrom() {
		assertEquals(new BlockDataSource("1.16.5"), DATA.require(754).source());
		assertEquals(new BlockDataSource("26.3"), DATA.require(777).source());
	}
}
