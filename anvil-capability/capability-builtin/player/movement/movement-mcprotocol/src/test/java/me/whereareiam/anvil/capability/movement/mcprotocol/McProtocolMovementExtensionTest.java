package me.whereareiam.anvil.capability.movement.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.movement.MovementOperations;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
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

class McProtocolMovementExtensionTest {
	private static final Position POSITION = Position.builder()
			.x(1.5).y(64).z(-2.5)
			.yaw(90).pitch(-15)
			.onGround(true)
			.build();

	@Test
	void isDiscoveredAsTheMcProtocolMovementExtension() {
		WorkerExtension<?> extension = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(McProtocolMovementExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.movement", extension.id());
		assertEquals(Optional.of("mcprotocol"), extension.libraryId());
		assertEquals(Object.class, extension.nativeSessionType());
	}

	@Test
	void bindsMovementThroughTheAdapterTheWorkerSelectedForThePlayersRelease() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player(packets);
		Operations operations = new Operations();

		new McProtocolMovementExtension().bind(player, operations);
		assertNull(operations.execute(MovementOperations.MOVE, POSITION));

		assertEquals(List.of(MovementPackets.class), player.requestedPorts);
		assertEquals(List.of("movement.move"), List.copyOf(operations.handlers.keySet()));
		assertSame(Player.SESSION, packets.session);
		assertSame(POSITION, packets.position);
		assertEquals(new ViewRotation(90, -15), player.rotation);
	}

	@Test
	void leavesTheCapabilityToTheWorkerWhenNoAdapterServesTheRelease() {
		AdapterUnavailableException unavailable = new AdapterUnavailableException("no mcprotocol segment selected for Minecraft 1.16.5 "
				+ "provides an adapter for " + MovementPackets.class.getName());
		Player player = new Player(null) {
			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				throw unavailable;
			}
		};
		Operations operations = new Operations();

		assertSame(unavailable, assertThrows(AdapterUnavailableException.class, () -> new McProtocolMovementExtension().bind(player, operations)));
		assertEquals(Map.of(), operations.handlers);
	}

	private static final class RecordingPackets implements MovementPackets<String> {
		private @Nullable String session;
		private @Nullable Position position;

		@Override
		public @NotNull Class<String> sessionType() {
			return String.class;
		}

		@Override
		public void move(@NotNull String session, @NotNull Position position) {
			this.session = session;
			this.position = position;
		}
	}

	private static class Player implements PlayerBindingContext<Object> {
		private static final String SESSION = "native session";

		private final @Nullable MovementPackets<?> packets;
		private final List<Class<?>> requestedPorts = new ArrayList<>();
		private @Nullable ViewRotation rotation;

		private Player(@Nullable MovementPackets<?> packets) {
			this.packets = packets;
		}

		public @NotNull String name() { return "Alice"; }
		public @NotNull UUID uniqueId() { return new UUID(0, 1); }
		public void connect() { throw new AssertionError(); }
		public void disconnect() { throw new AssertionError(); }
		public void rejoin() { throw new AssertionError(); }
		public @NotNull Object nativeSession() { return SESSION; }
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) { return nativeSession == SESSION; }
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) { throw new AssertionError(); }
		public @NotNull ViewRotation viewRotation() { throw new AssertionError(); }
		public void viewRotation(@NotNull ViewRotation rotation) { this.rotation = rotation; }
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) { throw new AssertionError(); }

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
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), request -> handler.apply((Q) request));
		}

		private <Q, R> @Nullable Object execute(ChannelOperation<Q, R> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}
}
