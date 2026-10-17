package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionBindingTest {
	@Test
	void targetsEveryLibraryAndSessionType() {
		SessionBinding binding = new SessionBinding();

		assertEquals("me.whereareiam.anvil.session", binding.id());
		assertEquals(Optional.empty(), binding.libraryId());
		assertEquals(Object.class, binding.nativeSessionType());
	}

	@Test
	void drivesThePlayerLifecycleWithoutTouchingTheNativeSession() {
		RecordingPlayer player = new RecordingPlayer();
		RecordingOperations operations = new RecordingOperations();
		new SessionBinding().bind(player, operations);

		assertNull(operations.execute(SessionOperations.CONNECT));
		assertNull(operations.execute(SessionOperations.REJOIN));
		assertNull(operations.execute(SessionOperations.DISCONNECT));

		assertEquals(List.of("connect", "rejoin", "disconnect"), player.calls);
		assertEquals(List.of("session.connect", "session.disconnect", "session.rejoin"), List.copyOf(operations.handlers.keySet()));
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> channelOperation, @NotNull Function<Q, R> handler) {
			handlers.put(channelOperation.getId(), (Function<Object, Object>) handler);
		}

		private @Nullable Object execute(ChannelOperation<Void, Void> operation) {
			return handlers.get(operation.getId()).apply(null);
		}
	}

	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final List<String> calls = new ArrayList<>();

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull UUID uniqueId() {
			return UUID.nameUUIDFromBytes("Alice".getBytes());
		}

		@Override
		public void connect() {
			calls.add("connect");
		}

		@Override
		public void disconnect() {
			calls.add("disconnect");
		}

		@Override
		public void rejoin() {
			calls.add("rejoin");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("The session binding must not use the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("The session binding must not use the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			throw new AssertionError("The session binding must not use the native session");
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			return new ViewRotation(0, 0);
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("The session binding must not rotate the view");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			throw new AssertionError("The worker emits connection events, not the session binding");
		}
	}
}
