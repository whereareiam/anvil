package me.whereareiam.anvil.integration.intellij.scenario.environment;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvironmentSessionTest {
	@Test
	void sessionLifecycleTakesPrecedenceOverRetainedProcessReports() {
		ScenarioDescriptor scenario = scenario(false);

		assertEquals(EnvironmentSession.Status.STARTING,
				EnvironmentSession.Status.of(scenario, snapshot(SessionState.STARTING, process("paper", ProcessState.READY))));
		assertEquals(EnvironmentSession.Status.STOPPED,
				EnvironmentSession.Status.of(scenario, snapshot(SessionState.STOPPED, process("paper", ProcessState.READY))));
		assertEquals(EnvironmentSession.Status.FAILED,
				EnvironmentSession.Status.of(scenario, snapshot(SessionState.FAILED, process("paper", ProcessState.READY))));
	}

	@Test
	void runningEnvironmentReportsPartialSetupAndFailureStates() {
		ScenarioDescriptor scenario = scenario(true);
		SessionSnapshot partial = snapshot(SessionState.RUNNING, process("paper", ProcessState.READY));
		assertEquals(EnvironmentSession.Status.PARTIALLY_RUNNING,
				EnvironmentSession.Status.of(scenario, partial));

		SessionSnapshot complete = partial.toBuilder()
				.processes(List.of(process("paper", ProcessState.READY), process("proxy", ProcessState.READY)))
				.build();
		assertEquals(EnvironmentSession.Status.SETUP_PENDING,
				EnvironmentSession.Status.of(scenario, complete));
		assertEquals(EnvironmentSession.Status.RUNNING,
				EnvironmentSession.Status.of(scenario, complete.toBuilder().setupComplete(true).build()));
		assertEquals(EnvironmentSession.Status.FAILED,
				EnvironmentSession.Status.of(scenario,
						complete.toBuilder().processes(List.of(process("paper", ProcessState.FAILED))).build()));
	}

	private static ScenarioDescriptor scenario(boolean setupAvailable) {
		return ScenarioDescriptor.builder()
				.definition("example.Scenarios")
				.name("example")
				.displayName("Example")
				.setupAvailable(setupAvailable)
				.process(definition("paper"))
				.process(definition("proxy"))
				.build();
	}

	private static ProcessDefinition definition(String name) {
		return ProcessDefinition.builder()
				.name(name)
				.displayName(name)
				.role(ProcessRole.SERVER)
				.platform("paper")
				.distribution("fixture")
				.javaRequirement("Java 21")
				.build();
	}

	private static SessionSnapshot snapshot(SessionState state, ProcessSnapshot... processes) {
		return SessionSnapshot.builder().state(state).processes(List.of(processes)).build();
	}

	private static ProcessSnapshot process(String name, ProcessState state) {
		return ProcessSnapshot.builder()
				.name(name)
				.executionId(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)))
				.displayName(name)
				.state(state)
				.host("127.0.0.1")
				.port(25565)
				.workDirectory("/workspace/" + name)
				.build();
	}
}
