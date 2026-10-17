package me.whereareiam.anvil.capability.movement.java;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
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
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaMovementExtensionTest {
	private static final Position POSITION = Position.builder()
			.x(1.5).y(64).z(-2.5)
			.yaw(90).pitch(-15)
			.onGround(true)
			.build();

	private final JavaMovementExtension extension = new JavaMovementExtension();

	@Test
	void isDiscoveredAsTheJavaMovementExtensionForEveryLibrary() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaMovementExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.movement", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void sendsTheMoveAsOnePacketAndSharesTheViewRotation() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);

		assertNull(operations.execute(MovementChannel.MOVE, POSITION));

		assertEquals(List.of("movement.move"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of(ServerboundMovePlayerPosRot.builder()
				.position(me.whereareiam.anvil.protocol.java.api.model.player.Position.builder()
						.x(1.5)
						.y(64)
						.z(-2.5)
						.build())
				.yaw(90)
				.pitch(-15)
				.onGround(true)
				.horizontalCollision(false)
				.build()), protocol.sent);
		assertEquals(new ViewRotation(90, -15), player.rotation);
	}

	@Test
	void leavesTheViewUnchangedWhileThePlayerIsDisconnected() {
		RecordingProtocol protocol = new RecordingProtocol(false);
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.execute(MovementChannel.MOVE, POSITION));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertEquals(List.of(), protocol.sent);
		assertNull(player.rotation);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer player = new RecordingPlayer(null);
		RecordingOperations operations = new RecordingOperations();

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(player, operations));
		assertEquals(Map.of(), operations.handlers);
	}

	/**
	 * Records the packets sent while connected and refuses them otherwise.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final boolean connected;
		private final List<ServerboundPacket> sent = new ArrayList<>();

		private RecordingProtocol(boolean connected) {
			this.connected = connected;
		}

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("Movement listens to no packets");
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			if (!connected) throw new IllegalStateException("Player Alice is not connected");

			sent.add(packet);
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return true;
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), (Function<Object, Object>) handler);
		}

		private <Q> @Nullable Object execute(OperationDescriptor<Q, ?> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}

	/**
	 * A player that speaks the given protocol, or none, and records the view rotation the binding sets.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private @Nullable ViewRotation rotation;

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			if (protocol == null) return PlayerBindingContext.super.protocol(type);

			return type.cast(protocol);
		}

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
			throw new AssertionError("Movement does not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Movement does not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Movement does not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("Movement uses the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("Movement uses the protocol, not the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			throw new AssertionError("Movement keeps no native listeners");
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Movement only records the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			this.rotation = rotation;
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			throw new AssertionError("Movement emits no events");
		}
	}
}
