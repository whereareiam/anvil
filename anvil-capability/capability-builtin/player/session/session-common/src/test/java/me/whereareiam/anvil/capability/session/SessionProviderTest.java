package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionProviderTest {
	@Test
	void describesTheSessionCapabilityForTheLibrariesWhoseWorkersBindSessions() {
		SessionProvider provider = new SessionProvider();

		assertEquals(SessionProvider.ID, provider.descriptor().getId());
		assertEquals(SessionProvider.ID, new SessionBinding().id());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of("mcprotocol"), provider.supportedLibraries());
		assertEquals(Session.class, provider.capability());
	}

	@Test
	void createsASessionThatFollowsConnectionEventsAndRequestsThroughThePlayersChannel() {
		RecordingChannel channel = new RecordingChannel();
		Session session = new SessionProvider().create(new ChannelContext(channel));
		assertEquals(List.of(PlayerConnectionEvent.CHANGED.getId(), PlayerConnectionEvent.DESTROYED.getId()), channel.subscriptions);
		assertTrue(channel.requests.isEmpty());

		session.connect();

		assertEquals(List.of(SessionOperations.CONNECT.getId()), channel.requests);
	}

	/**
	 * Records requests and subscriptions without delivering events.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<String> requests = new ArrayList<>();
		private final List<String> subscriptions = new ArrayList<>();

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(channelOperation.getId());
			return null;
		}

		@Override
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			subscriptions.add(eventDescriptor.getId());
			return () -> { };
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			throw new AssertionError("Creating a session does not wait");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(SessionProvider.ID);
		}
	}

	/**
	 * Supplies only the channel; the session needs no services, capabilities or observations.
	 */
	private record ChannelContext(CapabilityChannel channel) implements ProtocolPlayerCapabilityContext {
		@Override
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
			return Optional.empty();
		}

		@Override
		public @NotNull String playerName() {
			return "Alice";
		}

		@Override
		public @NotNull String clientVersion() {
			return "1.21.11";
		}

		@Override
		public @NotNull PlayerObservation observation() {
			throw new AssertionError("The session does not observe the player");
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			throw new AssertionError("The session registers no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return Optional.empty();
		}
	}
}
