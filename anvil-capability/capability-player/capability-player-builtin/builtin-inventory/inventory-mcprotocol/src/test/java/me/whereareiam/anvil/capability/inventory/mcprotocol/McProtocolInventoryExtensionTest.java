package me.whereareiam.anvil.capability.inventory.mcprotocol;

import me.whereareiam.anvil.capability.inventory.InventoryOperations;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPackets;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolInventoryExtensionTest {
	@Test
	void isDiscoveredAsTheMcProtocolInventoryExtension() {
		WorkerExtension<?> extension = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(McProtocolInventoryExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.inventory", extension.id());
		assertEquals(Optional.of("mcprotocol"), extension.libraryId());
		assertEquals(Object.class, extension.nativeSessionType());
	}

	@Test
	void bindsInventoryThroughTheAdapterTheWorkerSelectedForThePlayersRelease() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player(packets);
		Operations operations = new Operations();

		new McProtocolInventoryExtension().bind(player, operations);
		assertNull(operations.execute(InventoryOperations.SELECT, new SlotSelection(2)));
		packets.listener.opened(3);

		assertEquals(List.of(InventoryPackets.class), player.requestedPorts);
		assertEquals(List.of("inventory.select", "inventory.click"), List.copyOf(operations.handlers.keySet()));
		assertSame(Player.SESSION, packets.session);
		assertEquals(2, packets.selected);
		assertEquals(List.of(InventorySnapshot.builder().containerId(3).stateId(0).build()), player.snapshots);
	}

	@Test
	void leavesTheCapabilityToTheWorkerWhenNoAdapterServesTheRelease() {
		ProtocolUnavailableException unavailable = new ProtocolUnavailableException("no mcprotocol segment selected for Minecraft 1.16.5 "
				+ "provides an adapter for " + InventoryPackets.class.getName());
		Player player = new Player(null) {
			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				throw unavailable;
			}
		};
		Operations operations = new Operations();

		assertSame(unavailable, assertThrows(ProtocolUnavailableException.class, () -> new McProtocolInventoryExtension().bind(player, operations)));
		assertEquals(Map.of(), operations.handlers);
		assertTrue(player.snapshots.isEmpty());
	}

	private static final class RecordingPackets implements InventoryPackets<String> {
		private @Nullable String session;
		private int selected = -1;
		private @Nullable InventoryPacketListener listener;

		@Override
		public @NotNull Class<String> sessionType() {
			return String.class;
		}

		@Override
		public void selectHotbar(@NotNull String session, int slot) {
			this.session = session;
			selected = slot;
		}

		@Override
		public void click(@NotNull String session, @NotNull ContainerClick click) {
			throw new AssertionError("The test does not click");
		}

		@Override
		public @NotNull Runnable listen(@NotNull String session, @NotNull InventoryPacketListener listener) {
			this.listener = listener;
			return () -> { };
		}
	}

	private static class Player implements PlayerBindingContext<Object> {
		private static final String SESSION = "native session";

		private final @Nullable InventoryPackets<?> packets;
		private final List<Class<?>> requestedPorts = new ArrayList<>();
		private final List<InventorySnapshot> snapshots = new ArrayList<>();

		private Player(@Nullable InventoryPackets<?> packets) {
			this.packets = packets;
		}

		public @NotNull String name() { return "Alice"; }
		public @NotNull UUID uniqueId() { return new UUID(0, 1); }
		public void connect() { throw new AssertionError(); }
		public void disconnect() { throw new AssertionError(); }
		public void rejoin() { throw new AssertionError(); }
		public @NotNull Object nativeSession() { return SESSION; }
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) { return nativeSession == SESSION; }
		public @NotNull ViewRotation viewRotation() { throw new AssertionError(); }
		public void viewRotation(@NotNull ViewRotation rotation) { throw new AssertionError(); }

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			return listener.apply(SESSION);
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			assertSame(InventoryOperations.CHANGED, eventDescriptor);
			snapshots.add((InventorySnapshot) payload);
		}

		@Override
		public <P> @NotNull P adapter(@NotNull Class<P> port) {
			requestedPorts.add(port);
			return port.cast(packets);
		}
	}

	private static final class Operations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), request -> handler.apply((Q) request));
		}

		private <Q, R> @Nullable Object execute(OperationDescriptor<Q, R> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}
}
