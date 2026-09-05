package me.whereareiam.anvil.protocol.mcprotocol.worker.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkerMessageWriterTest {
	@Test
	void framesReadinessResultsAndPlayerEventsAsSeparateJsonLines() throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		WorkerMessageWriter writer = new WorkerMessageWriter(new PrintStream(output, true, StandardCharsets.UTF_8));
		ObjectMapper mapper = new ObjectMapper();
		writer.ready(774, Set.of("example.feature"));
		writer.success(3, mapper.createObjectNode().put("value", "line\nbreak"));
		writer.event("player", "example.event", mapper.createObjectNode().put("value", 5));
		writer.failure(4, new IllegalArgumentException("failed"));
		var lines = output.toString(StandardCharsets.UTF_8).lines().toList();
		assertEquals(4, lines.size());
		for (String line : lines)
			assertTrue(line.startsWith("ANVIL:"));
		assertEquals(774, mapper.readTree(lines.getFirst().substring(6)).path("protocol").asInt());
		var result = mapper.readTree(lines.get(1).substring(6));
		assertEquals(3, result.path("id").asInt());
		assertEquals("line\nbreak", result.at("/result/value").asText());
		assertEquals("example.event", mapper.readTree(lines.get(2).substring(6)).path("event").asText());
		assertFalse(mapper.readTree(lines.get(3).substring(6)).path("success").asBoolean());
	}
}
