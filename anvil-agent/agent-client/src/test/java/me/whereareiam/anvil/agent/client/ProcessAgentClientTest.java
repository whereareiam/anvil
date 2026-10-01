package me.whereareiam.anvil.agent.client;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
	void distinguishesUnavailableConnectionsFromUnobservedPlayersAcrossRestarts() {
		ProcessAgentClient client = new ProcessAgentClient();
		assertFalse(client.available());
		assertThrows(AgentUnavailableException.class, () -> client.identity("Alice"));

		List<String> calls = new ArrayList<>();
		client.attach(agent("first", calls));
		assertTrue(client.available());
		assertTrue(client.identity("Alice").isEmpty());
		client.close();
		assertFalse(client.available());
		assertThrows(AgentUnavailableException.class, () -> client.identity("Alice"));
		assertThrows(AgentUnavailableException.class, () -> client.request("probe", null, String.class));
		assertThrows(AgentUnavailableException.class, () -> client.executeCommand("probe"));

		client.attach(agent("second", calls));
		assertTrue(client.available());
		assertTrue(client.identity("Alice").isEmpty());
		client.close();
		assertFalse(client.available());
		assertEquals(List.of("first:Alice", "first:close", "second:Alice", "second:close"), calls);
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
					if (method.getName().equals("available")) return true;
					calls.add(generation + ":" + (arguments == null ? method.getName() : arguments[0]));
					if (method.getName().equals("identity")) return Optional.empty();
					return method.getName().equals("executeCommand") ? true : null;
				});
	}
}
