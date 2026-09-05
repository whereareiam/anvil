package me.whereareiam.anvil.testing.server.session;

import me.whereareiam.anvil.api.runtime.AnvilContext;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.engine.AnvilEngine;
import me.whereareiam.anvil.engine.model.EngineOptions;
import me.whereareiam.anvil.testing.server.scenario.Paper12111SystemScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioFailureCleanupSystemTest {
	@TempDir Path temporary;

	@Test
	void setupAssertionsRetainDiagnosticsAndStopTheStartedProcesses() throws Exception {
		var captured = new AtomicReference<AnvilContext>();
		AssertionError assertion = new AssertionError("fixture setup assertion");
		var scenario = new Paper12111SystemScenario().define().toBuilder().setupHook(context -> {
			captured.set(context);
			throw assertion;
		}).build();
		try (var engine = new AnvilEngine(EngineOptions.builder()
				.eulaAccepted(true).workDirectory(temporary).build())) {
			assertSame(assertion, assertThrows(AssertionError.class, () -> engine.start(scenario)));
		}
		var server = captured.get().server("server");
		assertEquals(ProcessState.STOPPED, server.state());
		assertTrue(Files.exists(server.workDirectory().resolve("anvil-console.log")));
		assertFalse(Files.readString(server.workDirectory().resolve("anvil-console.log")).isEmpty());
	}
}
