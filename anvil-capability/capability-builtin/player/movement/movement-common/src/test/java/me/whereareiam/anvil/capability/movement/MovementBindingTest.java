package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementBindingTest {
	private static final Position POSITION = Position.builder()
			.x(1.5).y(64).z(-2.5)
			.yaw(90).pitch(-15)
			.onGround(true)
			.build();

	@Test
	void sendsTheMoveThroughThePortAndSharesTheViewRotation() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer(new NativeSession());
		RecordingOperations operations = new RecordingOperations();
		new MovementBinding<>(packets).bind(player, operations);

		assertNull(operations.execute(MovementOperations.MOVE, POSITION));

		assertEquals(List.of("movement.move"), List.copyOf(operations.handlers.keySet()));
		assertSame(player.session, packets.session);
		assertSame(POSITION, packets.position);
		assertEquals(new ViewRotation(90, -15), player.rotation);
	}

	@Test
	void refusesASessionOfAnotherTypeBeforeChangingTheView() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer("not a native session");
		RecordingOperations operations = new RecordingOperations();
		new MovementBinding<>(packets).bind(player, operations);

		assertThrows(ClassCastException.class, () -> operations.execute(MovementOperations.MOVE, POSITION));

		assertNull(packets.position);
		assertNull(player.rotation);
	}

	@Test
	void leavesTheViewUnchangedWhileThePlayerIsDisconnected() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer(null);
		RecordingOperations operations = new RecordingOperations();
		new MovementBinding<>(packets).bind(player, operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.execute(MovementOperations.MOVE, POSITION));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertNull(packets.position);
		assertNull(player.rotation);
	}

	private static final class NativeSession {
	}

	private static final class RecordingPackets implements MovementPackets<NativeSession> {
		private NativeSession session;
		private Position position;

		@Override
		public @NotNull Class<NativeSession> sessionType() {
			return NativeSession.class;
		}

		@Override
		public void move(@NotNull NativeSession session, @NotNull Position position) {
			this.session = session;
			this.position = position;
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> channelOperation, @NotNull Function<Q, R> handler) {
			handlers.put(channelOperation.getId(), (Function<Object, Object>) handler);
		}

		private <Q> @Nullable Object execute(ChannelOperation<Q, ?> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}

	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable Object session;
		private @Nullable ViewRotation rotation;

		private RecordingPlayer(@Nullable Object session) {
			this.session = session;
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
		}

		@Override
		public void disconnect() {
		}

		@Override
		public void rejoin() {
		}

		@Override
		public @NotNull Object nativeSession() {
			if (session == null) throw new IllegalStateException("Player Alice is not connected");

			return session;
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			return nativeSession == session;
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			throw new AssertionError("Movement keeps no native listeners");
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			return rotation == null ? new ViewRotation(0, 0) : rotation;
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			this.rotation = rotation;
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			throw new AssertionError("Movement emits no events");
		}
	}
}
