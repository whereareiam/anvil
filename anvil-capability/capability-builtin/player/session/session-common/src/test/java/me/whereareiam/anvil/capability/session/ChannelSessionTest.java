package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.session.model.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelSessionTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	@Test
	void requestsLifecycleOperationsFromTheWorker() {
		RecordingChannel channel = new RecordingChannel();
		ChannelSession session = new ChannelSession(channel);

		session.connect();
		session.rejoin();
		session.disconnect();

		assertEquals(List.of("session.connect", "session.rejoin", "session.disconnect"), channel.requests);
	}

	@Test
	void followsConnectionEventsAndKeepsTheKickReason() {
		RecordingChannel channel = new RecordingChannel();
		ChannelSession session = new ChannelSession(channel);

		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(true, null));
		session.connected(TIMEOUT);
		assertEquals(SessionState.builder().connected(true).build(), session.state());

		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(false, "Server closed"));
		session.disconnected(TIMEOUT);
		assertEquals("Server closed", session.kicked(TIMEOUT));
		assertEquals(SessionState.builder().connected(false).kickReason("Server closed").build(), session.state());
	}

	@Test
	void forgetsTheKickReasonWhenConnectingAgain() {
		RecordingChannel channel = new RecordingChannel();
		ChannelSession session = new ChannelSession(channel);
		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(false, "Server closed"));

		session.rejoin();

		assertEquals(SessionState.builder().connected(false).build(), session.state());
		assertThrows(IllegalStateException.class, () -> session.kicked(TIMEOUT));
	}

	@Test
	void reportsDisconnectedOnceThePlayerIsDestroyed() {
		RecordingChannel channel = new RecordingChannel();
		ChannelSession session = new ChannelSession(channel);
		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(true, null));

		channel.publish(PlayerConnectionEvent.DESTROYED, null);

		session.disconnected(TIMEOUT);
		assertEquals(SessionState.builder().connected(false).build(), session.state());
	}

	/**
	 * Records requests and delivers published events synchronously; a wait whose condition does not hold fails
	 * immediately instead of timing out.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<String> requests = new ArrayList<>();
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

		private <E> void publish(EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			listeners.getOrDefault(eventDescriptor.getId(), List.of()).forEach(listener -> listener.accept(payload));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(channelOperation.getId());
			return null;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			listeners.computeIfAbsent(eventDescriptor.getId(), ignored -> new ArrayList<>()).add((Consumer<Object>) listener);
			return () -> listeners.get(eventDescriptor.getId()).remove(listener);
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(SessionProvider.ID);
		}
	}
}
