package me.whereareiam.anvil.integration.intellij.view.window.main.component.status;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusPresentationTest {
	@Test
	void processPresentationKeepsRuntimeStateTyped() {
		assertEquals("Not started", StatusPresentation.processLabel(null, SessionState.IDLE));
		assertEquals("Running", StatusPresentation.processLabel(ProcessState.READY, SessionState.RUNNING));
		assertEquals(StatusPresentation.Tone.RUNNING,
				StatusPresentation.processTone(ProcessState.READY, SessionState.RUNNING));
		assertEquals("Status unavailable",
				StatusPresentation.processLabel(ProcessState.READY, SessionState.FAILED));
		assertEquals("Status unavailable · Last reported: Running · Environment failed",
				StatusPresentation.processDescription(ProcessState.READY, SessionState.FAILED));
	}

	@Test
	void environmentPresentationUsesTheCalculatedStateAndCounts() {
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		SessionSnapshot snapshot = SessionSnapshot.builder()
				.state(SessionState.RUNNING)
				.processes(List.of(process("paper")))
				.build();

		assertEquals("Partially running · 1 of 2 processes running",
				StatusPresentation.environmentDescription(scenario, snapshot, EnvironmentState.PARTIALLY_RUNNING));
		assertEquals("0 of 2 running",
				StatusPresentation.processSummary(scenario,
						snapshot.toBuilder().state(SessionState.STOPPED).build()));
	}

	private static ProcessSnapshot process(String name) {
		return ProcessSnapshot.builder()
				.name(name)
				.executionId(EnvironmentSessionFixture.executionId(name))
				.displayName(name)
				.state(ProcessState.READY)
				.host("127.0.0.1")
				.port(25565)
				.workDirectory("/workspace/" + name)
				.build();
	}
}
