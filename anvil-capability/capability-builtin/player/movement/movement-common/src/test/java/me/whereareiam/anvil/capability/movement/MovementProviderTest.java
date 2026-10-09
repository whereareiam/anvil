package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
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

class MovementProviderTest {
	@Test
	void describesTheMovementCapabilityForTheLibrariesWithAWorkerSide() {
		MovementProvider provider = new MovementProvider();

		assertEquals(MovementProvider.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of("mcprotocol"), provider.supportedLibraries());
		assertEquals(Movement.class, provider.capability());
	}

	@Test
	void sendsEachMoveThroughThePlayersChannelWithoutRequestingOnCreation() {
		RecordingChannel channel = new RecordingChannel();
		Movement movement = new MovementProvider().create(new ChannelContext(channel));
		assertTrue(channel.requests.isEmpty());

		Position position = Position.builder().x(1).y(64).z(-2).yaw(90).pitch(10).onGround(true).build();
		movement.move(position);

		assertEquals(List.of(MovementOperations.MOVE.getId()), channel.requests);
		assertEquals(List.of(position), channel.payloads);
	}

	/**
	 * Records each request's operation and payload.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<String> requests = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(channelOperation.getId());
			payloads.add(request);
			return null;
		}

		@Override
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			return () -> { };
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			throw new AssertionError("Movement does not wait");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(MovementProvider.ID);
		}
	}

	/**
	 * Supplies only the channel; movement needs no services, capabilities or observations.
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
			throw new AssertionError("Movement does not observe the player");
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			throw new AssertionError("Movement registers no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return Optional.empty();
		}
	}
}
