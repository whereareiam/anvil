package me.whereareiam.anvil.edition.java.data.packet;

import me.whereareiam.anvil.edition.java.data.packet.model.PacketDataSource;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketRegistryTest {
	private static final PacketDataSource SOURCE = PacketDataSource.builder()
			.repository("PrismarineJS/minecraft-data")
			.version("3.117.0")
			.commit("c".repeat(40))
			.protocol("data/pc/1.21.11/protocol.json")
			.build();

	@Test
	void refusesTwoPacketsOfOneName() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new PacketRegistry(774,
				SOURCE, Map.of(ConnectionState.PLAY, List.of("ping", "ping"))));

		assertEquals("Protocol 774 names two PLAY packets alike", failure.getMessage());
	}

	@Test
	void refusesABlankName() {
		assertThrows(IllegalArgumentException.class, () -> new PacketRegistry(774, SOURCE,
				Map.of(ConnectionState.PLAY, List.of(" "))));
	}
}
