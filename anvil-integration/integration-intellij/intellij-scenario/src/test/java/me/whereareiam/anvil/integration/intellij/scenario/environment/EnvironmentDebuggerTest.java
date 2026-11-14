package me.whereareiam.anvil.integration.intellij.scenario.environment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.whereareiam.anvil.integration.intellij.scenario.log.SessionLogEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentDebuggerTest {
	private final List<String> attached = new ArrayList<>();
	private final EnvironmentDebugger debugger = new EnvironmentDebugger((process, port) -> attached.add(process + ":" + port));
	private long sequence;

	@Test void attachesWhenTheAnnouncementCompletesAcrossChunks() {
		UUID lobby = UUID.randomUUID();
		append("lobby", lobby, "Listening for transport dt_socket at add");
		assertEquals(List.of(), attached);

		append("lobby", lobby, "ress: 41235\nStarting minecraft server\n");

		assertEquals(List.of("lobby:41235"), attached);
	}

	@Test void attachesToEachGenerationAndProcess() {
		append("lobby", UUID.randomUUID(), "Listening for transport dt_socket at address: 40001\r\n");
		append("velocity", UUID.randomUUID(), "Listening for transport dt_socket at address: 40002\n");
		append("lobby", UUID.randomUUID(), "Listening for transport dt_socket at address: 40003\n");

		assertEquals(List.of("lobby:40001", "velocity:40002", "lobby:40003"), attached);
	}

	@Test void ignoresPreparationOutputAndOtherLines() {
		append(null, null, "Listening for transport dt_socket at address: 1\n");
		append("lobby", UUID.randomUUID(), "[Server] Done (3.2s)!\n");

		assertEquals(List.of(), attached);
	}

	private void append(String process, UUID execution, String text) {
		debugger.appended(new SessionLogEntry(process, execution, sequence++, text, false));
	}
}
