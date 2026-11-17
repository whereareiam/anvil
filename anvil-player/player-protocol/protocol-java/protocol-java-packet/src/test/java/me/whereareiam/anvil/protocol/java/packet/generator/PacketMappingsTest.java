package me.whereareiam.anvil.protocol.java.packet.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketMappingsTest {
	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void readsTheNamesOfEachStateItHas() throws Exception {
		Map<ConnectionState, List<String>> names = PacketMappings.clientbound("test", protocol("""
				"login": %s, "play": %s
				""".formatted(section("\"0x00\": \"disconnect\", \"0x01\": \"success\""),
				section("\"0x01\": \"spawn_entity\", \"0x00\": \"bundle_delimiter\""))));

		assertEquals(List.of("disconnect", "success"), names.get(ConnectionState.LOGIN));
		assertEquals(List.of("bundle_delimiter", "spawn_entity"), names.get(ConnectionState.PLAY));
		assertEquals(null, names.get(ConnectionState.CONFIGURATION));
	}

	@Test
	void refusesAGapInTheIds() throws Exception {
		JsonNode protocol = protocol("\"play\": " + section("\"0x00\": \"bundle_delimiter\", \"0x02\": \"animation\""));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> PacketMappings.clientbound("test", protocol));

		assertEquals("test play.toClient has no packet with ID 1", failure.getMessage());
	}

	@Test
	void refusesAFileWithoutPlayPackets() throws Exception {
		JsonNode protocol = protocol("\"login\": " + section("\"0x00\": \"disconnect\""));

		assertThrows(IllegalStateException.class, () -> PacketMappings.clientbound("test", protocol));
	}

	private static String section(String mappings) {
		return """
				{"toClient": {"types": {"packet": ["container", [
				  {"name": "name", "type": ["mapper", {"type": "varint", "mappings": {%s}}]},
				  {"name": "params", "type": ["switch", {"compareTo": "name", "fields": {}}]}
				]]}}}
				""".formatted(mappings);
	}

	private static JsonNode protocol(String sections) throws Exception {
		return JSON.readTree("{" + sections + "}");
	}
}
