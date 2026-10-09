package me.whereareiam.anvil.integration.intellij.view.window.main;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioPresentationTest {
	@Test
	void scenarioIdentityIgnoresPresentationMetadata() {
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		ScenarioDescriptor saved = ScenarioDescriptor.builder()
				.definition(scenario.getDefinition())
				.name(scenario.getName())
				.displayName("Saved configuration")
				.build();

		assertEquals("example.AuthenticationScenarios/registration", ScenarioPresentation.identity(scenario));
		assertTrue(ScenarioPresentation.sameScenario(scenario, saved));
		assertFalse(ScenarioPresentation.sameScenario(scenario, saved.toBuilder().name("returning").build()));
	}

	@Test
	void onlySingleProcessScenariosWithoutSetupAreStandalone() {
		ScenarioDescriptor grouped = WindowTestSupport.scenario();
		ScenarioDescriptor single = grouped.toBuilder().clearProcesses().process(grouped.getProcesses().getFirst()).build();

		assertNull(ScenarioPresentation.standaloneProcess(grouped));
		assertSame(single.getProcesses().getFirst(), ScenarioPresentation.standaloneProcess(single));
		assertNull(ScenarioPresentation.standaloneProcess(single.toBuilder().setupAvailable(true).build()));
	}

	@Test
	void processesKeepDeclarationOrderAndAppendUndeclaredLiveProcesses() {
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		SessionSnapshot snapshot = snapshot(live("proxy", ProcessState.READY), live("extra", ProcessState.STARTING));

		List<ScenarioPresentation.ScenarioProcess> processes = ScenarioPresentation.processes(scenario, snapshot);

		assertEquals(List.of("paper", "proxy", "extra"), processes.stream().map(ScenarioPresentation.ScenarioProcess::getName).toList());
		assertNull(processes.get(0).getLive());
		assertSame(scenario.getProcesses().getLast(), processes.get(1).getDefinition());
		assertEquals(ProcessState.READY, processes.get(1).getLive().getState());
		assertNull(processes.get(2).getDefinition());
	}

	@Test
	void processCommandsFollowLiveState() {
		SessionSnapshot snapshot = snapshot(live("paper", ProcessState.READY), live("proxy", ProcessState.FAILED));

		assertTrue(ScenarioPresentation.canStartProcess(null));
		assertFalse(ScenarioPresentation.canStartProcess(ScenarioPresentation.liveProcess(snapshot, "paper")));
		assertTrue(ScenarioPresentation.canStartProcess(ScenarioPresentation.liveProcess(snapshot, "proxy")));
		assertNull(ScenarioPresentation.liveProcess(snapshot, null));
		assertEquals("Start proxy", ScenarioPresentation.startProcessLabel(ProcessRole.PROXY));
		assertEquals("Start server", ScenarioPresentation.startProcessLabel(null));
	}

	private static SessionSnapshot snapshot(ProcessSnapshot... processes) {
		return SessionSnapshot.builder()
				.state(SessionState.RUNNING)
				.processes(List.of(processes))
				.build();
	}

	private static ProcessSnapshot live(String name, ProcessState state) {
		return ProcessSnapshot.builder()
				.name(name)
				.executionId(EnvironmentSessionFixture.executionId(name))
				.displayName(name)
				.state(state)
				.host("127.0.0.1")
				.workDirectory("/workspace/" + name)
				.build();
	}
}
