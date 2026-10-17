package me.whereareiam.anvil.capability.dialogs.mcprotocol;

import me.whereareiam.anvil.capability.dialogs.DialogsOperations;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPacketListener;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.AdapterUnavailableException;
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

class McProtocolDialogsExtensionTest {
	@Test
	void isDiscoveredAsTheMcProtocolDialogsExtension() {
		WorkerExtension<?> extension = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(McProtocolDialogsExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.dialogs", extension.id());
		assertEquals(Optional.of("mcprotocol"), extension.libraryId());
		assertEquals(Object.class, extension.nativeSessionType());
	}

	@Test
	void bindsDialogsThroughTheAdapterTheWorkerSelectedForThePlayersRelease() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player(packets);
		Operations operations = new Operations();

		new McProtocolDialogsExtension().bind(player, operations);
		assertNull(operations.execute(DialogsOperations.CLICK, DialogClick.command("login secret")));

		assertEquals(List.of(DialogsPackets.class), player.requestedPorts);
		assertEquals(List.of("dialogs.click"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of(DialogClick.command("login secret")), packets.sent);
		assertSame(Player.SESSION, packets.listened);
		assertEquals(List.of("dialogs.reset"), player.emitted);
	}

	@Test
	void leavesTheCapabilityToTheWorkerWhenNoAdapterServesTheRelease() {
		AdapterUnavailableException unavailable = new AdapterUnavailableException("no mcprotocol segment selected for Minecraft 1.21.1 "
				+ "provides an adapter for " + DialogsPackets.class.getName());
		Player player = new Player(null) {
			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				throw unavailable;
			}
		};
		Operations operations = new Operations();

		assertSame(unavailable, assertThrows(AdapterUnavailableException.class, () -> new McProtocolDialogsExtension().bind(player, operations)));
		assertEquals(Map.of(), operations.handlers);
	}

	private static final class RecordingPackets implements DialogsPackets<String> {
		private final List<DialogClick> sent = new ArrayList<>();
		private @Nullable String listened;

		@Override
		public @NotNull Class<String> sessionType() {
			return String.class;
		}

		@Override
		public void click(@NotNull String session, @NotNull DialogClick click) {
			sent.add(click);
		}

		@Override
		public @NotNull Runnable listen(@NotNull String session, @NotNull DialogsPacketListener listener) {
			listened = session;
			return () -> listened = null;
		}
	}

	private static class Player implements PlayerBindingContext<Object> {
		private static final String SESSION = "native session";

		private final @Nullable DialogsPackets<?> packets;
		private final List<Class<?>> requestedPorts = new ArrayList<>();
		private final List<String> emitted = new ArrayList<>();

		private Player(@Nullable DialogsPackets<?> packets) {
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
			emitted.add(eventDescriptor.getId());
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
