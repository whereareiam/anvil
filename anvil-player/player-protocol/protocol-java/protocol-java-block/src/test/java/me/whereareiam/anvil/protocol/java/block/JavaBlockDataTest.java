package me.whereareiam.anvil.protocol.java.block;

import me.whereareiam.anvil.protocol.java.block.exception.BlockDataUnavailableException;
import me.whereareiam.anvil.protocol.java.block.model.BlockDataSource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaBlockDataTest {
	private static final JavaBlockData DATA = JavaBlockData.shipped();

	@Test
	void shipsBlockDataForEveryProtocolMinecraftDataHas() {
		assertEquals(Set.of(754, 756, 758, 762, 766, 767, 774, 775), DATA.protocols());
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
	void listsTheSupportedProtocolsMinecraftDataLacksAsMissing() {
		assertEquals(Set.of(776, 777), DATA.missingProtocols());
		assertTrue(DATA.find(776).isEmpty());
	}

	@Test
	void reportsWhyAMissingProtocolHasNoBlockData() {
		BlockDataUnavailableException failure = assertThrows(BlockDataUnavailableException.class, () -> DATA.require(777));

		assertEquals(777, failure.getProtocol());
		assertEquals("No block data for protocol 777: PrismarineJS/minecraft-data 3.117.0 has no block data for it",
				failure.getMessage());
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
	void namesTheSourceOfTheData() {
		assertEquals(BlockDataSource.builder()
				.repository("PrismarineJS/minecraft-data")
				.version("3.117.0")
				.commit("15411f050f040e4c5df09352a75a9912ad8c3eeb")
				.blocks("data/pc/1.16.2/blocks.json")
				.collisionShapes("data/pc/1.16.1/blockCollisionShapes.json")
				.build(), DATA.require(754).source());
	}
}
