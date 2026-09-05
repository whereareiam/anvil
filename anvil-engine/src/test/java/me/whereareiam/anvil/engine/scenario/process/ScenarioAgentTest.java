package me.whereareiam.anvil.engine.scenario.process;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioAgentTest {
	@Test
	void refreshesBorrowedAgentHandlesAndClosesBothGenerationsExactlyOnce() {
		List<String> calls = new ArrayList<>();
		ScenarioAgent borrowed = new ScenarioAgent(agent("old", calls));
		borrowed.executeCommand("before");
		borrowed.close();
		assertThrows(AgentException.class, () -> borrowed.executeCommand("during"));
		borrowed.replace(agent("new", calls));
		borrowed.executeCommand("after");
		borrowed.close();
		borrowed.close();

		assertEquals(List.of("old:before", "old:close", "new:after", "new:close"), calls);
		assertThrows(AgentException.class, () -> borrowed.executeCommand("closed"));
	}

	private AgentClient agent(String generation, List<String> calls) {
		return (AgentClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{AgentClient.class},
				(proxy, method, arguments) -> {
					calls.add(generation + ":" + (arguments == null ? method.getName() : arguments[0]));
					return method.getName().equals("executeCommand") ? true : null;
				});
	}
}
