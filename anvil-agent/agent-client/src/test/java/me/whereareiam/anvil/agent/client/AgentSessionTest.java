package me.whereareiam.anvil.agent.client;

import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentSessionTest {
	@Test
	void connectsOnlyWhenRequestedUsingHostPortAndItsOwnCredentials() {
		List<String> calls = new ArrayList<>();
		List<String> tokens = new ArrayList<>();
		AgentConnectionProvider connections = (port, token, timeout) -> {
			assertEquals(30001, port);
			assertEquals(Duration.ofSeconds(20), timeout);
			tokens.add(token);
			return agent("first", calls);
		};

		ProcessAgentClient client = new ProcessAgentClient();
		try (AgentSession session = session(client, connections)) {
			Map<String, String> environment = session.environment();
			String token = environment.get("ANVIL_AGENT_TOKEN");
			assertEquals(32, HexFormat.of().parseHex(token).length);
			assertEquals("20001", environment.get("ANVIL_AGENT_PORT"));
			assertEquals("127.0.0.1", environment.get("ANVIL_AGENT_BIND"));
			assertThrows(UnsupportedOperationException.class, () -> environment.put("new", "value"));
			assertTrue(tokens.isEmpty());
			assertThrows(AgentException.class, () -> client.executeCommand("before-start"));

			session.connect();
			session.connect();
			assertEquals(List.of(token), tokens);
			assertTrue(client.executeCommand("ready"));
		}

		assertEquals(List.of("first:ready", "first:close"), calls);
		assertThrows(AgentException.class, () -> client.executeCommand("after-cleanup"));
	}

	@Test
	void issuesDifferentTokensForDifferentGenerations() {
		AgentConnectionProvider connections = (port, token, timeout) -> {
			throw new AssertionError("Constructing a session must not connect");
		};
		ProcessAgentClient client = new ProcessAgentClient();
		try (AgentSession first = session(client, connections);
		     AgentSession second = session(client, connections)) {
			assertNotEquals(first.environment().get("ANVIL_AGENT_TOKEN"),
					second.environment().get("ANVIL_AGENT_TOKEN"));
		}
	}

	@Test
	void cannotConnectAfterClosingAnUnstartedSession() {
		AgentSession session = session(new ProcessAgentClient(), (port, token, timeout) -> {
			throw new AssertionError("A closed session must not connect");
		});
		session.close();
		session.close();

		assertThrows(IllegalStateException.class, session::connect);
	}

	@Test
	void staleSessionCleanupCannotDisconnectTheReplacementGeneration() {
		List<String> calls = new ArrayList<>();
		ProcessAgentClient client = new ProcessAgentClient();
		try (AgentSession first = session(client, (port, token, timeout) -> agent("first", calls));
		     AgentSession second = session(client, (port, token, timeout) -> agent("second", calls))) {
			first.connect();
			client.close();
			second.connect();
			first.close();
			assertTrue(client.executeCommand("current"));
		}

		assertEquals(List.of("first:close", "second:current", "second:close"), calls);
	}

	@Test
	void preservesAttachmentFailureAndClosesTheRejectedConnectionExactlyOnce() {
		List<String> calls = new ArrayList<>();
		RuntimeException cleanupFailure = new IllegalArgumentException("Rejected connection cleanup failed");
		AtomicInteger rejectedCloses = new AtomicInteger();
		AgentClient rejected = (AgentClient) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{AgentClient.class}, (proxy, method, arguments) -> {
					if (!method.getName().equals("close"))
						throw new AssertionError("Rejected connection must not handle requests");

					rejectedCloses.incrementAndGet();
					throw cleanupFailure;
				});
		ProcessAgentClient client = new ProcessAgentClient();
		try (AgentSession first = session(client, (port, token, timeout) -> agent("first", calls));
		     AgentSession second = session(client, (port, token, timeout) -> rejected)) {
			first.connect();
			var failure = assertThrows(IllegalStateException.class, second::connect);
			assertArrayEquals(new Throwable[]{cleanupFailure}, failure.getSuppressed());
			second.close();
			assertTrue(client.executeCommand("still-connected"));
		}

		assertEquals(1, rejectedCloses.get());
		assertEquals(List.of("first:still-connected", "first:close"), calls);
	}

	@Test
	void connectionFailureLeavesTheStableHandleUnavailable() {
		AgentException connectionFailure = new AgentException("Agent did not become ready");
		ProcessAgentClient client = new ProcessAgentClient();
		try (AgentSession session = session(client, (port, token, timeout) -> {
			throw connectionFailure;
		})) {
			assertSame(connectionFailure, assertThrows(AgentException.class, session::connect));
			assertThrows(AgentException.class, () -> client.executeCommand("unavailable"));
		}
	}

	@Test
	void failedCleanupDoesNotCloseTheConnectionAgainOrPreventTheNextGeneration() {
		List<String> calls = new ArrayList<>();
		RuntimeException cleanupFailure = new AgentException("Connection cleanup failed");
		AtomicInteger failedCloses = new AtomicInteger();
		AgentClient failed = (AgentClient) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{AgentClient.class}, (proxy, method, arguments) -> {
					if (!method.getName().equals("close"))
						throw new AssertionError("No requests were expected");

					failedCloses.incrementAndGet();
					throw cleanupFailure;
				});
		ProcessAgentClient client = new ProcessAgentClient();
		try (AgentSession first = session(client, (port, token, timeout) -> failed);
		     AgentSession second = session(client, (port, token, timeout) -> agent("second", calls))) {
			first.connect();
			assertSame(cleanupFailure, assertThrows(AgentException.class, first::close));
			first.close();
			second.connect();
			assertTrue(client.executeCommand("current"));
		}

		assertEquals(1, failedCloses.get());
		assertEquals(List.of("second:current", "second:close"), calls);
	}

	private AgentSession session(ProcessAgentClient client, AgentConnectionProvider connections) {
		return AgentSession.builder()
				.client(client)
				.connections(connections)
				.bindPort(20001)
				.bindAddress("127.0.0.1")
				.connectPort(30001)
				.build();
	}

	private AgentClient agent(String generation, List<String> calls) {
		return (AgentClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{AgentClient.class},
				(proxy, method, arguments) -> {
					calls.add(generation + ":" + (arguments == null ? method.getName() : arguments[0]));
					return method.getName().equals("executeCommand") ? true : null;
				});
	}
}
