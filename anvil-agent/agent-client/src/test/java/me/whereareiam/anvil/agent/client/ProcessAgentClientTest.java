package me.whereareiam.anvil.agent.client;

import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessAgentClientTest {
	@Test
	void refreshesBorrowedAgentHandlesAndClosesBothGenerationsExactlyOnce() {
		List<String> calls = new ArrayList<>();
		ProcessAgentClient borrowed = new ProcessAgentClient();
		borrowed.attach(agent("old", calls));
		borrowed.executeCommand("before");
		borrowed.close();
		assertThrows(AgentException.class, () -> borrowed.executeCommand("during"));
		borrowed.attach(agent("new", calls));
		borrowed.executeCommand("after");
		borrowed.close();
		borrowed.close();

		assertEquals(List.of("old:before", "old:close", "new:after", "new:close"), calls);
		assertThrows(AgentException.class, () -> borrowed.executeCommand("closed"));
	}

	@Test
	void preservesAttachmentFailureWhenClosingTheRejectedConnectionAlsoFails() {
		List<String> calls = new ArrayList<>();
		IllegalArgumentException cleanup = new IllegalArgumentException("Rejected transport cleanup failed");
		AgentClient rejected = (AgentClient) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{AgentClient.class}, (proxy, method, arguments) -> {
					if (method.getName().equals("close")) throw cleanup;
					throw new AssertionError("Rejected connection must not handle requests");
				});

		try (ProcessAgentClient borrowed = new ProcessAgentClient()) {
			borrowed.attach(agent("active", calls));
			var failure = assertThrows(IllegalStateException.class, () -> borrowed.attach(rejected));
			assertEquals("Close the current agent connection before attaching another", failure.getMessage());
			assertArrayEquals(new Throwable[]{cleanup}, failure.getSuppressed());
			borrowed.executeCommand("still-connected");
		}

		assertEquals(List.of("active:still-connected", "active:close"), calls);
	}

	private AgentClient agent(String generation, List<String> calls) {
		return (AgentClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{AgentClient.class},
				(proxy, method, arguments) -> {
					calls.add(generation + ":" + (arguments == null ? method.getName() : arguments[0]));
					return method.getName().equals("executeCommand") ? true : null;
				});
	}
}
