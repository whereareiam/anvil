package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.engine.AnvilException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioRestartTest {
	@Test
	void refreshesBorrowedAgentHandlesAndClosesBothGenerationsExactlyOnce() {
		List<String> calls = new ArrayList<>();
		ScenarioResources resources = new ScenarioResources(Duration.ofSeconds(1), success -> assertTrue(success));
		resources.addAgent("proxy", agent("old", calls));
		AgentClient borrowed = resources.agents().get("proxy");
		resources.registerRestart("proxy", () -> {
			resources.detachAgent("proxy");
			assertThrows(AgentException.class, () -> borrowed.executeCommand("during"));
			resources.addAgent("proxy", agent("new", calls));
		});
		borrowed.executeCommand("before");
		resources.restart("proxy");
		assertSame(borrowed, resources.agents().get("proxy"));
		borrowed.executeCommand("after");
		resources.close(true);
		resources.close(true);
		assertEquals(List.of("old:before", "old:close", "new:after", "new:close"), calls);
		assertThrows(AnvilException.class, () -> resources.restart("proxy"));
	}

	@Test
	void caughtRestartFailuresStillFinalizeAsUnsuccessful() {
		List<Boolean> outcomes = new ArrayList<>();
		ScenarioResources resources = new ScenarioResources(Duration.ofSeconds(1), outcomes::add);
		resources.registerRestart("proxy", () -> { throw new IllegalStateException("startup failed"); });
		assertThrows(IllegalStateException.class, () -> resources.restart("proxy"));
		resources.close(true);
		assertEquals(List.of(false), outcomes);
	}

	private AgentClient agent(String generation, List<String> calls) {
		return (AgentClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{AgentClient.class},
				(proxy, method, arguments) -> {
					calls.add(generation + ":" + (arguments == null ? method.getName() : arguments[0]));
					return method.getName().equals("executeCommand") ? true : null;
				});
	}
}
