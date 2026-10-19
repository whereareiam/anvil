package me.whereareiam.anvil.protocol.java.data;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.data.exception.BlockDataUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaBlockDataTest {
	private static final JavaBlockData DATA = JavaBlockData.shipped();

	@Test
	void shipsBlockDataForEveryReleaseMinecraftDataHas() {
		assertEquals(versions("1.16.4", "1.16.5", "1.17.1", "1.18.2", "1.19.4", "1.20.5", "1.20.6", "1.21", "1.21.1",
				"1.21.11", "26.1", "26.1.1", "26.1.2"), DATA.versions());
	}

	@Test
	void listsTheSupportedReleasesMinecraftDataLacksAsMissing() {
		assertEquals(versions("26.2", "26.3"), DATA.missingVersions());
		assertTrue(DATA.find(MinecraftVersion.parse("26.2")).isEmpty());
	}

	@Test
	void reportsWhyAMissingReleaseHasNoBlockData() {
		BlockDataUnavailableException failure = assertThrows(BlockDataUnavailableException.class,
				() -> DATA.require(MinecraftVersion.parse("26.3")));

		assertEquals(MinecraftVersion.parse("26.3"), failure.getVersion());
		assertEquals("No block data for Minecraft 26.3: PrismarineJS/minecraft-data 3.117.0 has no block data for these "
				+ "versions", failure.getMessage());
	}

	@Test
	void refusesAVersionAnvilDoesNotSupport() {
		BlockDataUnavailableException failure = assertThrows(BlockDataUnavailableException.class,
				() -> DATA.require(MinecraftVersion.parse("1.12.2")));

		assertTrue(failure.getMessage().startsWith("No block data for Minecraft 1.12.2: Anvil supports no such version"),
				failure.getMessage());
		assertTrue(DATA.find(MinecraftVersion.parse("1.20.4")).isEmpty());
	}

	@Test
	void versionsOfOneProtocolShareOneRegistry() {
		BlockRegistry registry = DATA.require(MinecraftVersion.parse("26.1"));

		assertSame(registry, DATA.require(MinecraftVersion.parse("26.1.2")));
		assertEquals(List.copyOf(versions("26.1", "26.1.1", "26.1.2")), registry.minecraftVersions());
	}

	@Test
	void namesTheSourceOfTheData() {
		assertEquals("PrismarineJS/minecraft-data 3.117.0 15411f050f040e4c5df09352a75a9912ad8c3eeb "
						+ "data/pc/1.16.2/blocks.json data/pc/1.16.1/blockCollisionShapes.json",
				DATA.require(MinecraftVersion.parse("1.16.5")).source());
	}

	private static Set<MinecraftVersion> versions(String... versions) {
		return Stream.of(versions).map(MinecraftVersion::parse).collect(Collectors.toCollection(TreeSet::new));
	}
}
