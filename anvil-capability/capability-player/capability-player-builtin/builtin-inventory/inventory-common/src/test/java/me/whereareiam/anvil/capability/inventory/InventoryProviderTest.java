package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.inventory.channel.InventoryChannel;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
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

class InventoryProviderTest {
	@Test
	void describesTheInventoryCapabilityForTheLibrariesWithAWorkerSide() {
		InventoryProvider provider = new InventoryProvider();

		assertEquals("me.whereareiam.anvil.inventory", provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Inventory.class, provider.capability());
	}

	@Test
	void requestsThroughThePlayersChannelOnlyOnceTheInventoryIsUsed() {
		RecordingChannel channel = new RecordingChannel();
		Inventory inventory = new InventoryProvider().create(new ChannelContext(channel));
		assertTrue(channel.requests.isEmpty());
		assertEquals(List.of(InventoryChannel.CHANGED.getId()), channel.subscriptions);

		inventory.selectSlot(0);
		inventory.click(4, InventoryClick.LEFT, 0);

		assertEquals(List.of(InventoryChannel.SELECT.getId(), InventoryChannel.CLICK.getId()), channel.requests);
		assertEquals(List.of(new SlotSelection(0), new InventoryAction(4, InventoryClick.LEFT, 0)), channel.payloads);
	}

	/**
	 * Records each request's operation and payload and each subscribed event.
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
			throw new AssertionError("The test does not wait");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(InventoryChannel.ID);
		}
	}

	/**
	 * Supplies only the channel; inventory needs no services or capabilities.
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
			throw new AssertionError("Inventory registers no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return Optional.empty();
		}
	}
}
