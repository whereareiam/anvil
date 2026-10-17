package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.entities.channel.EntitiesChannel;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitiesProviderTest {
	@Test
	void describesTheEntitiesCapabilityForTheLibrariesWithAWorkerSide() {
		EntitiesProvider provider = new EntitiesProvider();

		assertEquals("me.whereareiam.anvil.entities", EntitiesChannel.ID);
		assertEquals(EntitiesChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of("mcprotocol"), provider.supportedLibraries());
		assertEquals(Entities.class, provider.capability());
	}

	@Test
	void observesEntityChangesThroughThePlayersChannelWithoutRequestingOnCreation() {
		RecordingChannel channel = new RecordingChannel();
		Entities entities = new EntitiesProvider().create(new ChannelContext(channel));

		assertEquals(List.of(EntitiesChannel.CHANGED.getId()), channel.subscriptions);
		assertTrue(channel.requests.isEmpty());
		assertTrue(entities.watch().unread().isEmpty());
	}

	/**
	 * Records requests and subscriptions without delivering events.
	 */
	private static final class RecordingChannel implements ProtocolChannel {
		private final List<String> requests = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();
		private final List<String> subscriptions = new ArrayList<>();

		@Override
		public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
			requests.add(operation.getId());
			payloads.add(request);
			return null;
		}

		@Override
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			subscriptions.add(eventDescriptor.getId());
			return () -> { };
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			throw new AssertionError("Creating entities does not wait");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(EntitiesChannel.ID);
		}
	}

	/**
	 * Supplies only the channel; entities need no services or capabilities.
	 */
	private record ChannelContext(ProtocolChannel channel) implements ProtocolPlayerCapabilityContext {
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
		public @NotNull PlayerProfile requestedProfile() {
			return new PlayerProfile("Alice", UUID.nameUUIDFromBytes("Alice".getBytes()));
		}

		@Override
		public @Nullable PlayerProfile grantedProfile() {
			return null;
		}

		@Override
		public @NotNull PlayerConnection connection() {
			return PlayerConnection.to("proxy");
		}


		@Override
		public void onDestroy(@NotNull Runnable action) {
			throw new AssertionError("Entities register no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return Optional.empty();
		}
	}
}
