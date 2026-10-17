package me.whereareiam.anvil.capability.sounds;

import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.packet.SoundsPackets;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SoundsBindingTest {
	@Test
	void registersNoOperations() {
		RecordingOperations operations = new RecordingOperations();

		new SoundsBinding<>(new RecordingPackets()).bind(new RecordingPlayer(), operations);

		assertEquals(Map.of(), operations.handlers);
	}

	@Test
	void emitsSoundsOfTheCurrentSessionOnlyAndListensToEachNewSession() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		new SoundsBinding<>(packets).bind(player, new RecordingOperations());
		NativeSession first = new NativeSession();
		NativeSession second = new NativeSession();

		player.startSession(first);
		packets.deliver(first, sound("minecraft:entity.player.levelup"));
		player.session = second;
		packets.deliver(first, sound("minecraft:late"));
		player.startSession(second);
		packets.deliver(second, sound("minecraft:block.note_block.pling"));

		assertEquals(List.of(sound("minecraft:entity.player.levelup"), sound("minecraft:block.note_block.pling")), player.emitted);
		assertEquals(List.of(second), List.copyOf(packets.listeners.keySet()));
	}

	@Test
	void removesTheListenerOfTheCurrentSessionWhenClosed() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		WorkerBinding binding = new SoundsBinding<>(packets).bind(player, new RecordingOperations());
		NativeSession session = new NativeSession();
		player.startSession(session);

		binding.close();
		packets.deliver(session, sound("minecraft:after.close"));

		assertEquals(Map.of(), packets.listeners);
		assertEquals(List.of(), player.emitted);
	}

	@Test
	void refusesToListenToASessionOfAnotherType() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		new SoundsBinding<>(packets).bind(player, new RecordingOperations());

		assertThrows(ClassCastException.class, () -> player.startSession("not a native session"));

		assertEquals(Map.of(), packets.listeners);
	}

	private static HeardSound sound(String key) {
		return HeardSound.builder()
				.key(key)
				.category(SoundCategory.PLAYERS)
				.volume(1F)
				.pitch(1F)
				.entityId(7)
				.build();
	}

	private static final class NativeSession {
	}

	/**
	 * Delivers sounds to the listener of each session.
	 */
	private static final class RecordingPackets implements SoundsPackets<NativeSession> {
		private final Map<NativeSession, Consumer<HeardSound>> listeners = new LinkedHashMap<>();

		@Override
		public @NotNull Class<NativeSession> sessionType() {
			return NativeSession.class;
		}

		@Override
		public @NotNull Runnable listen(@NotNull NativeSession session, @NotNull Consumer<HeardSound> heard) {
			listeners.put(session, heard);
			return () -> listeners.remove(session);
		}

		private void deliver(NativeSession session, HeardSound sound) {
			Consumer<HeardSound> listener = listeners.get(session);
			if (listener != null) listener.accept(sound);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), (Function<Object, Object>) handler);
		}
	}

	/**
	 * A player whose native session generations the test starts: each {@link #startSession(Object)} closes the listeners
	 * of the previous generation and binds them for the new session, as the worker does before a session connects.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final List<HeardSound> emitted = new ArrayList<>();
		private @Nullable Object session;
		private @Nullable Function<Object, Subscription> listener;
		private @Nullable Subscription generation;

		private void startSession(Object nativeSession) {
			if (generation != null) generation.close();
			generation = null;
			session = nativeSession;
			if (listener != null) generation = listener.apply(nativeSession);
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
			throw new AssertionError("Sounds do not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Sounds do not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Sounds do not drive the lifecycle");
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
			this.listener = listener;
			return () -> {
				this.listener = null;
				if (generation != null) generation.close();
				generation = null;
			};
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Sounds do not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Sounds do not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			assertSame(SoundsOperations.HEARD, eventDescriptor);
			emitted.add((HeardSound) payload);
		}
	}
}
