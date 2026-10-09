package me.whereareiam.anvil.capability.interaction;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.packet.InteractionPackets;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionBindingTest {
	private static final BlockPosition POSITION = BlockPosition.builder().x(4).y(-60).z(-7).build();

	@Test
	void registersTheItemBlockAndEntityOperations() {
		RecordingOperations operations = new RecordingOperations();

		WorkerBinding binding = new InteractionBinding<>(new RecordingPackets()).bind(new RecordingPlayer(new NativeSession()), operations);

		assertEquals(List.of("interaction.item", "interaction.block", "interaction.entity"), List.copyOf(operations.handlers.keySet()));
		assertDoesNotThrow(binding::close);
	}

	@Test
	void swingsBeforeUsingTheItemWithTheNextSequenceAndTheSharedView() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer(new NativeSession());
		player.rotation = new ViewRotation(90, -15);
		RecordingOperations operations = new RecordingOperations();
		new InteractionBinding<>(packets).bind(player, operations);

		assertNull(operations.execute(InteractionOperations.ITEM, new ItemUse(Hand.OFF)));

		assertEquals(List.of(
				new Sent(player.session, "swing", List.of(Hand.OFF)),
				new Sent(player.session, "useItem", List.of(Hand.OFF, 1, 90F, -15F))
		), packets.sent);
	}

	@Test
	void usesTheItemOnTheBlockBeforeSwinging() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer(new NativeSession());
		RecordingOperations operations = new RecordingOperations();
		new InteractionBinding<>(packets).bind(player, operations);

		assertNull(operations.execute(InteractionOperations.BLOCK, new BlockUse(4, -60, -7, BlockFace.WEST, Hand.MAIN)));

		assertEquals(List.of(
				new Sent(player.session, "useItemOn", List.of(POSITION, BlockFace.WEST, Hand.MAIN, 1)),
				new Sent(player.session, "swing", List.of(Hand.MAIN))
		), packets.sent);
	}

	@Test
	void sendsTheEntityInteractionBeforeSwingingWithoutTakingASequence() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer(new NativeSession());
		RecordingOperations operations = new RecordingOperations();
		new InteractionBinding<>(packets).bind(player, operations);

		assertNull(operations.execute(InteractionOperations.ENTITY, new EntityUse(42, EntityInteraction.ATTACK, Hand.MAIN)));
		operations.execute(InteractionOperations.ITEM, new ItemUse(Hand.MAIN));

		assertEquals(List.of(
				new Sent(player.session, "entity", List.of(42, EntityInteraction.ATTACK, Hand.MAIN)),
				new Sent(player.session, "swing", List.of(Hand.MAIN)),
				new Sent(player.session, "swing", List.of(Hand.MAIN)),
				new Sent(player.session, "useItem", List.of(Hand.MAIN, 1, 0F, 0F))
		), packets.sent);
	}

	@Test
	void numbersItemAndBlockUsesInOneSequencePerPlayer() {
		RecordingPackets packets = new RecordingPackets();
		InteractionBinding<NativeSession> binding = new InteractionBinding<>(packets);
		RecordingOperations alice = new RecordingOperations();
		RecordingOperations bob = new RecordingOperations();
		binding.bind(new RecordingPlayer(new NativeSession()), alice);
		binding.bind(new RecordingPlayer(new NativeSession()), bob);

		alice.execute(InteractionOperations.ITEM, new ItemUse(Hand.MAIN));
		alice.execute(InteractionOperations.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN));
		bob.execute(InteractionOperations.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN));
		alice.execute(InteractionOperations.ITEM, new ItemUse(Hand.MAIN));

		assertEquals(List.of(1, 2, 1, 3), packets.sequences());
	}

	@Test
	void refusesASessionOfAnotherTypeBeforeSendingOrTakingASequence() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer("not a native session");
		RecordingOperations operations = new RecordingOperations();
		new InteractionBinding<>(packets).bind(player, operations);

		assertThrows(ClassCastException.class, () -> operations.execute(InteractionOperations.ITEM, new ItemUse(Hand.MAIN)));
		assertThrows(ClassCastException.class, () -> operations.execute(InteractionOperations.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN)));
		assertThrows(ClassCastException.class, () -> operations.execute(InteractionOperations.ENTITY, new EntityUse(42, EntityInteraction.INTERACT, Hand.MAIN)));
		assertEquals(List.of(), packets.sent);

		player.session = new NativeSession();
		operations.execute(InteractionOperations.ITEM, new ItemUse(Hand.MAIN));
		assertEquals(List.of(1), packets.sequences());
	}

	@Test
	void sendsNothingWhileThePlayerIsDisconnected() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer(null);
		RecordingOperations operations = new RecordingOperations();
		new InteractionBinding<>(packets).bind(player, operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.execute(InteractionOperations.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN)));
		assertThrows(IllegalStateException.class, () -> operations.execute(InteractionOperations.ITEM, new ItemUse(Hand.MAIN)));
		assertThrows(IllegalStateException.class, () -> operations.execute(InteractionOperations.ENTITY, new EntityUse(42, EntityInteraction.INTERACT, Hand.MAIN)));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertEquals(List.of(), packets.sent);
	}

	private static final class NativeSession {
	}

	/**
	 * One packet the port was asked to send, with the session it was sent through and its arguments in order.
	 */
	private record Sent(Object session, String packet, List<Object> arguments) {
	}

	private static final class RecordingPackets implements InteractionPackets<NativeSession> {
		private final List<Sent> sent = new ArrayList<>();

		@Override
		public @NotNull Class<NativeSession> sessionType() {
			return NativeSession.class;
		}

		@Override
		public void swing(@NotNull NativeSession session, @NotNull Hand hand) {
			sent.add(new Sent(session, "swing", List.of(hand)));
		}

		@Override
		public void useItem(@NotNull NativeSession session, @NotNull Hand hand, int sequence, float yaw, float pitch) {
			sent.add(new Sent(session, "useItem", List.of(hand, sequence, yaw, pitch)));
		}

		@Override
		public void useItemOn(@NotNull NativeSession session, @NotNull BlockPosition position, @NotNull BlockFace face, @NotNull Hand hand, int sequence) {
			sent.add(new Sent(session, "useItemOn", List.of(position, face, hand, sequence)));
		}

		@Override
		public void entity(@NotNull NativeSession session, int entityId, @NotNull EntityInteraction kind, @NotNull Hand hand) {
			sent.add(new Sent(session, "entity", List.of(entityId, kind, hand)));
		}

		private List<Object> sequences() {
			return sent.stream()
					.filter(packet -> packet.packet().startsWith("useItem"))
					.map(packet -> packet.packet().equals("useItem") ? packet.arguments().get(1) : packet.arguments().get(3))
					.toList();
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
		private @Nullable Object session;
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
			throw new AssertionError("Interaction does not connect players");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Interaction does not disconnect players");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Interaction does not reconnect players");
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
			throw new AssertionError("Interaction keeps no native listeners");
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			return rotation == null ? new ViewRotation(0, 0) : rotation;
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Interaction does not change the view");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			throw new AssertionError("Interaction emits no events");
		}
	}
}
