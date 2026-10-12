package me.whereareiam.anvil.integration.intellij.scenario.window;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.ScenarioFixtures;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSessionFixture;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ScenarioPresentationTest {
	@Test
	void scenarioIdentityIsItsDefinitionClass() {
		ScenarioDescriptor scenario = ScenarioFixtures.scenario();
		ScenarioDescriptor saved = ScenarioDescriptor.builder()
				.definition(scenario.getDefinition())
				.name("renamed")
				.displayName("Saved configuration")
				.build();

		assertEquals("example.AuthenticationScenarios", ScenarioPresentation.identity(scenario));
		assertEquals(ScenarioPresentation.identity(scenario), ScenarioPresentation.identity(saved));
	}

	@Test
	void onlySingleProcessScenariosWithoutSetupAreStandalone() {
		ScenarioDescriptor grouped = ScenarioFixtures.scenario();
		ScenarioDescriptor single = grouped.toBuilder().clearProcesses().process(grouped.getProcesses().getFirst()).build();

		assertNull(ScenarioPresentation.standaloneProcess(grouped));
		assertSame(single.getProcesses().getFirst(), ScenarioPresentation.standaloneProcess(single));
		assertNull(ScenarioPresentation.standaloneProcess(single.toBuilder().setupAvailable(true).build()));
	}

	@Test
	void processesKeepDeclarationOrderAndAppendUndeclaredLiveProcesses() {
		ScenarioDescriptor scenario = ScenarioFixtures.scenario();
		SessionSnapshot snapshot = snapshot(live("proxy", ProcessState.READY), live("extra", ProcessState.STARTING));

		List<ScenarioPresentation.ScenarioProcess> processes = ScenarioPresentation.processes(scenario, snapshot);

		assertEquals(List.of("paper", "proxy", "extra"), processes.stream().map(ScenarioPresentation.ScenarioProcess::getName).toList());
		assertNull(processes.get(0).getLive());
		assertSame(scenario.getProcesses().getLast(), processes.get(1).getDefinition());
		assertEquals(ProcessState.READY, processes.get(1).getLive().getState());
		assertNull(processes.get(2).getDefinition());
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
