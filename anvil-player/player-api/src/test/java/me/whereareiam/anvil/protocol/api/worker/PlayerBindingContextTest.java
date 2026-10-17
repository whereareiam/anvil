package me.whereareiam.anvil.protocol.api.worker;

import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerBindingContextTest {
	@Test
	void followsEachConnectionThroughTheNativeSessionsWithoutHandingThemOut() {
		RecordingContext player = new RecordingContext();
		List<String> events = new ArrayList<>();

		Subscription connections = player.bindConnection(() -> {
			events.add("opened");
			return () -> events.add("ended");
		});
		player.connection("first").close();
		player.connection("second");
		connections.close();

		assertEquals(List.of("opened", "ended", "opened"), events);
		assertEquals(List.of("first", "second"), player.sessions);
		assertFalse(player.bound);
	}

	/**
	 * A context whose native sessions are names; it binds one listener and hands it each new session.
	 */
	private static final class RecordingContext implements PlayerBindingContext<String> {
		private final List<String> sessions = new ArrayList<>();
		private Function<String, Subscription> listener;
		private boolean bound;

		private Subscription connection(String session) {
			sessions.add(session);
			return listener.apply(session);
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<String, Subscription> listener) {
			this.listener = listener;
			bound = true;
			return () -> bound = false;
		}

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull UUID uniqueId() {
			return new UUID(0, 1);
		}

		@Override
		public void connect() {
		}

		@Override
		public void disconnect() {
		}

		@Override
		public void rejoin() {
		}

		@Override
		public @NotNull String nativeSession() {
			return sessions.getLast();
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull String nativeSession) {
			return nativeSession.equals(nativeSession());
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			return new ViewRotation(0, 0);
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
		}
	}
}
