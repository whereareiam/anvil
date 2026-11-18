package me.whereareiam.anvil.edition.java.data.packet;

import me.whereareiam.anvil.edition.java.data.packet.exception.PacketDataUnavailableException;
import me.whereareiam.anvil.edition.java.data.packet.model.PacketDataSource;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaPacketDataTest {
	private static final JavaPacketData DATA = JavaPacketData.shipped();

	@Test
	void shipsPacketDataForEveryProtocolMinecraftDataHas() {
		assertEquals(Set.of(754, 756, 758, 762, 766, 767, 774, 775), DATA.protocols());
	}

	@Test
	void readsEveryShippedProtocol() {
		for (int protocol : DATA.protocols()) {
			PacketRegistry registry = DATA.require(protocol);

			assertEquals(protocol, registry.protocol());
			assertTrue(registry.clientbound(ConnectionState.PLAY).contains("map_chunk"), "Protocol " + protocol
					+ " has no chunk packet");
			assertTrue(registry.clientbound(ConnectionState.LOGIN).contains("success"), "Protocol " + protocol
					+ " has no login success");
		}
	}

	@Test
	void namesPacketsByStateAndId() {
		PacketRegistry registry = DATA.require(774);

		assertEquals(Optional.of("bundle_delimiter"), registry.clientbound(ConnectionState.PLAY, 0x00));
		assertEquals(Optional.of("block_change"), registry.clientbound(ConnectionState.PLAY, 0x08));
		assertEquals(Optional.of("cookie_request"), registry.clientbound(ConnectionState.CONFIGURATION, 0x00));
		assertEquals(OptionalInt.of(0x08), registry.clientboundId(ConnectionState.PLAY, "block_change"));
		assertTrue(registry.clientbound(ConnectionState.PLAY, 0x200).isEmpty());
		assertTrue(registry.clientboundId(ConnectionState.PLAY, "level_chunk_with_light").isEmpty());
	}

	@Test
	void hasNoConfigurationPacketsBeforeTheStateExisted() {
		assertTrue(DATA.require(754).clientbound(ConnectionState.CONFIGURATION).isEmpty());
		assertTrue(DATA.require(754).clientbound(ConnectionState.CONFIGURATION, 0).isEmpty());
	}

	@Test
	void listsTheSupportedProtocolsMinecraftDataLacksAsMissing() {
		assertEquals(Set.of(776, 777), DATA.missingProtocols());
		assertTrue(DATA.find(776).isEmpty());
	}

	@Test
	void reportsWhyAMissingProtocolHasNoPacketData() {
		PacketDataUnavailableException failure = assertThrows(PacketDataUnavailableException.class, () -> DATA.require(777));

		assertEquals(777, failure.getProtocol());
		assertEquals("No packet data for protocol 777: PrismarineJS/minecraft-data 3.117.0 has no protocol file for it",
				failure.getMessage());
	}

	@Test
	void refusesAProtocolAnvilDoesNotSupport() {
		PacketDataUnavailableException failure = assertThrows(PacketDataUnavailableException.class, () -> DATA.require(340));

		assertTrue(failure.getMessage().startsWith("No packet data for protocol 340: Anvil supports no such protocol"),
				failure.getMessage());
	}

	@Test
	void readsEachProtocolOnce() {
		assertSame(DATA.require(775), DATA.require(775));
	}

	@Test
	void namesTheSourceOfTheData() {
		assertEquals(PacketDataSource.builder()
				.repository("PrismarineJS/minecraft-data")
				.version("3.117.0")
				.commit("15411f050f040e4c5df09352a75a9912ad8c3eeb")
				.protocol("data/pc/1.16.2/protocol.json")
				.build(), DATA.require(754).source());
	}
}
