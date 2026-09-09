package me.whereareiam.anvil.launcher.assembly.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnection;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AgentRequestChannelTest {
	@Test
	void dispatchesTheDeclaredOperationAndResponseSchemaThroughTheBorrowedConnection() {
		var connection = new Connection(ignored -> 42);
		var channel = new AgentRequestChannel(connection);
		var operation = new ChannelOperation<>("external.fixture.count", String.class, Integer.class);

		assertEquals(42, channel.request(operation, "hello"));
		assertEquals(List.of(new Invocation(operation.getId(), "hello", Integer.class)), connection.invocations);
		assertEquals(0, connection.closed);
	}

	@Test
	void acceptsVoidRequestsWithoutFabricatingAPayload() {
		var connection = new Connection(ignored -> "ready");
		var channel = new AgentRequestChannel(connection);
		var operation = new ChannelOperation<>("external.fixture.state", Void.class, String.class);

		assertEquals("ready", channel.request(operation, null));
		assertEquals(List.of(new Invocation(operation.getId(), null, String.class)), connection.invocations);
	}

	@Test
	void acceptsCommandsWithoutAResponsePayload() {
		var connection = new Connection(ignored -> null);
		var channel = new AgentRequestChannel(connection);
		var operation = new ChannelOperation<>("external.fixture.set", String.class, Void.class);

		assertNull(channel.request(operation, "value"));
		assertEquals(List.of(new Invocation(operation.getId(), "value", Void.class)), connection.invocations);
	}

	@Test
	void preservesAnAbsentResultForOperationsWhoseResponseSchemaIsNotVoid() {
		var connection = new Connection(ignored -> null);
		var channel = new AgentRequestChannel(connection);
		var operation = new ChannelOperation<>("external.fixture.identity", String.class, String.class);

		assertNull(channel.request(operation, "unknown"));
		assertEquals(List.of(new Invocation(operation.getId(), "unknown", String.class)), connection.invocations);
	}

	@Test
	void rejectsMissingRequestsBeforeDispatchUnlessTheSchemaIsVoid() {
		var connection = new Connection(ignored -> "unused");
		var channel = new AgentRequestChannel(connection);
		var operation = new ChannelOperation<>("external.fixture.echo", String.class, String.class);

		var failure = assertThrows(IllegalArgumentException.class, () -> channel.request(operation, null));
		assertTrue(failure.getMessage().contains(operation.getId()));
		assertTrue(connection.invocations.isEmpty());
	}

	@Test
	@SuppressWarnings({"rawtypes", "unchecked"})
	void rejectsRequestsOutsideTheDeclaredSchemaBeforeDispatch() {
		var connection = new Connection(ignored -> "unused");
		var channel = new AgentRequestChannel(connection);
		ChannelOperation<Object, String> operation = (ChannelOperation) new ChannelOperation<>(
				"external.fixture.echo", String.class, String.class
		);

		assertThrows(ClassCastException.class, () -> channel.request(operation, 42));
		assertTrue(connection.invocations.isEmpty());
	}

	@Test
	void propagatesTransportFailuresWithoutReplacingTheirIdentity() {
		var failure = new AgentException("Agent is reconnecting");
		var connection = new Connection(ignored -> { throw failure; });
		var channel = new AgentRequestChannel(connection);
		var operation = new ChannelOperation<>("external.fixture.echo", String.class, String.class);

		assertSame(failure, assertThrows(AgentException.class, () -> channel.request(operation, "hello")));
		assertEquals(0, connection.closed);
	}

	@RequiredArgsConstructor
	private static final class Connection implements AgentConnection {
		private final Function<Invocation, Object> response;
		private final List<Invocation> invocations = new ArrayList<>();
		private int closed;

		@Override
		public <T> @Nullable T request(@NotNull String operation, @Nullable Object arguments, @NotNull Class<T> responseType) {
			var invocation = new Invocation(operation, arguments, responseType);
			invocations.add(invocation);
			return responseType.cast(response.apply(invocation));
		}

		@Override
		public void close() {
			closed++;
		}
	}

	private record Invocation(String operation, @Nullable Object arguments, Class<?> responseType) { }
}
