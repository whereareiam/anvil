package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.packet.EntitiesPackets;
import me.whereareiam.anvil.capability.entities.packet.model.EntityMoved;
import me.whereareiam.anvil.capability.entities.packet.model.EntityPacket;
import me.whereareiam.anvil.capability.entities.packet.model.EntitySpawned;
import me.whereareiam.anvil.capability.entities.packet.model.WorldCleared;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitiesBindingTest {
	@Test
	void emitsTheFactsOfEachSessionNumberedByConnection() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player();

		new EntitiesBinding<>(packets).bind(player, new Operations());
		player.open("first");
		packets.listeners.getLast().accept(spawn(7));
		player.open("second");
		packets.listeners.getLast().accept(spawn(7));
		packets.listeners.getLast().accept(new WorldCleared());

		assertEquals(List.of("first", "second"), packets.sessions);
		assertEquals(List.of(1, 2, 2), player.emitted.stream().map(change -> change.getEntity().getConnection()).toList());
		assertEquals(List.of(EntityChangeKind.APPEARED, EntityChangeKind.APPEARED, EntityChangeKind.DISAPPEARED),
				player.emitted.stream().map(EntityChange::getKind).toList());
	}

	@Test
	void readsTheEntitiesOfTheLatestSessionWithoutEmittingMoves() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player();
		Operations operations = new Operations();
		new EntitiesBinding<>(packets).bind(player, operations);
		assertEquals(new KnownEntities(List.of()), operations.execute(EntitiesOperations.KNOWN, null));

		player.open("first");
		packets.listeners.getLast().accept(spawn(7));
		player.open("second");
		packets.listeners.getLast().accept(spawn(8));
		packets.listeners.getLast().accept(EntityMoved.builder().entityId(8).x(4).build());

		List<SeenEntity> known = operations.execute(EntitiesOperations.KNOWN, null).getEntities();
		assertEquals(List.of("entities.known"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of(8), known.stream().map(SeenEntity::getId).toList());
		assertEquals(4.0, known.getFirst().getX());
		assertEquals(2, known.getFirst().getConnection());
		assertEquals(2, player.emitted.size());
	}

	@Test
	void detachesTheListenerOfTheCurrentSessionWhenClosed() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player();

		WorkerBinding binding = new EntitiesBinding<>(packets).bind(player, new Operations());
		player.open("first");
		binding.close();

		assertEquals(1, packets.detached);
	}

	@Test
	void refusesASessionOfAnotherType() {
		Player player = new Player();
		new EntitiesBinding<>(new RecordingPackets()).bind(player, new Operations());

		assertThrows(ClassCastException.class, () -> player.open(42));
	}

	private static EntitySpawned spawn(int entityId) {
		return EntitySpawned.builder().entityId(entityId).uniqueId(new UUID(0, entityId)).type("minecraft:armor_stand").build();
	}

	private static final class RecordingPackets implements EntitiesPackets<String> {
		private final List<String> sessions = new ArrayList<>();
		private final List<Consumer<EntityPacket>> listeners = new ArrayList<>();
		private int detached;

		@Override
		public @NotNull Class<String> sessionType() {
			return String.class;
		}

		@Override
		public @NotNull Runnable listen(@NotNull String session, @NotNull Consumer<EntityPacket> packets) {
			sessions.add(session);
			listeners.add(packets);
			return () -> detached++;
		}
	}

	/**
	 * A player whose native sessions the test opens one after another.
	 */
	private static final class Player implements PlayerBindingContext<Object> {
		private final List<EntityChange> emitted = new ArrayList<>();
		private @Nullable Function<Object, Subscription> listener;
		private @Nullable Subscription current;

		private void open(Object session) {
			if (current != null) current.close();
			current = listener.apply(session);
		}

		public @NotNull String name() { return "Alice"; }
		public @NotNull UUID uniqueId() { return new UUID(0, 1); }
		public void connect() { throw new AssertionError(); }
		public void disconnect() { throw new AssertionError(); }
		public void rejoin() { throw new AssertionError(); }
		public @NotNull Object nativeSession() { throw new AssertionError("Entities send nothing"); }
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) { throw new AssertionError(); }
		public @NotNull ViewRotation viewRotation() { throw new AssertionError(); }
		public void viewRotation(@NotNull ViewRotation rotation) { throw new AssertionError(); }

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			this.listener = listener;
			return () -> {
				if (current != null) current.close();
			};
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			assertSame(EntitiesOperations.CHANGED, eventDescriptor);
			emitted.add((EntityChange) payload);
		}
	}

	private static final class Operations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), request -> handler.apply((Q) request));
		}

		private <Q, R> R execute(ChannelOperation<Q, R> operation, @Nullable Q request) {
			return operation.getResponseType().cast(handlers.get(operation.getId()).apply(request));
		}
	}
}
