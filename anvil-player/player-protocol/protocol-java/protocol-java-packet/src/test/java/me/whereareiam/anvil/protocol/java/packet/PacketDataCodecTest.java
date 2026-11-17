package me.whereareiam.anvil.protocol.java.packet;

import me.whereareiam.anvil.protocol.java.api.type.ConnectionState;
import me.whereareiam.anvil.protocol.java.packet.model.PacketDataSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketDataCodecTest {
	private static final PacketDataSource SOURCE = PacketDataSource.builder()
			.repository("PrismarineJS/minecraft-data")
			.version("3.117.0")
			.commit("c".repeat(40))
			.protocol("data/pc/1.21.11/protocol.json")
			.build();

	@Test
	void readsTheRegistryItWrote() throws IOException {
		PacketRegistry registry = new PacketRegistry(774, SOURCE, Map.of(
				ConnectionState.LOGIN, List.of("disconnect", "success"),
				ConnectionState.PLAY, List.of("bundle_delimiter", "spawn_entity")
		));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PacketDataCodec.writeRegistry(registry, bytes);

		PacketRegistry read = PacketDataCodec.readRegistry(new ByteArrayInputStream(bytes.toByteArray()));

		assertEquals(774, read.protocol());
		assertEquals(SOURCE, read.source());
		assertEquals(List.of("disconnect", "success"), read.clientbound(ConnectionState.LOGIN));
		assertEquals(List.of(), read.clientbound(ConnectionState.CONFIGURATION));
		assertEquals(List.of("bundle_delimiter", "spawn_entity"), read.clientbound(ConnectionState.PLAY));
	}

	@Test
	void readsTheIndexItWrote() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PacketDataCodec.writeIndex(new JavaPacketData(Set.of(774, 754), Map.of(777, "no file")), bytes);

		JavaPacketData read = PacketDataCodec.readIndex(new ByteArrayInputStream(bytes.toByteArray()));

		assertEquals(Set.of(754, 774), read.protocols());
		assertEquals(Set.of(777), read.missingProtocols());
		assertEquals("no file", read.missingReason(777));
	}
}
