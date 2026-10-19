package me.whereareiam.anvil.capability.interaction.java;

import me.whereareiam.anvil.capability.interaction.channel.InteractionChannel;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSwingAnimation;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundPunch;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.java.api.type.Direction;
import me.whereareiam.anvil.protocol.java.api.type.InteractAction;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaInteractionExtensionTest {
	private static final Set<Class<? extends JavaPacket>> INTERACTION_PACKETS = Set.of(
			ServerboundSwing.class,
			ServerboundUseItem.class,
			ServerboundUseItemOn.class,
			ServerboundInteract.class,
			ServerboundAttack.class
	);

	private static final Dimension OVERWORLD = new Dimension("minecraft:overworld", -64, 384);

	private final JavaInteractionExtension extension = new JavaInteractionExtension();

	@Test
	void isDiscoveredAsTheJavaInteractionExtensionForEveryLibrary() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaInteractionExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.interaction", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void registersTheItemBlockAndEntityOperations() {
		RecordingOperations operations = new RecordingOperations();

		WorkerBinding binding = extension.bind(new RecordingPlayer(new RecordingProtocol(INTERACTION_PACKETS)),
				operations);

		assertEquals(List.of("interaction.item", "interaction.block", "interaction.entity"), List.copyOf(operations.handlers.keySet()));
		assertDoesNotThrow(binding::close);
	}

	@Test
	void swingsBeforeUsingTheItemWithTheNextSequenceAndTheSharedView() {
		RecordingProtocol protocol = new RecordingProtocol(INTERACTION_PACKETS);
		RecordingPlayer player = new RecordingPlayer(protocol);
		player.rotation = new ViewRotation(90, -15);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);

		assertNull(operations.execute(InteractionChannel.ITEM, new ItemUse(Hand.OFF)));

		assertEquals(List.of(
				new ServerboundSwing(InteractionHand.OFF_HAND),
				ServerboundUseItem.builder().hand(InteractionHand.OFF_HAND).sequence(1).yaw(90).pitch(-15).build()
		), protocol.sent);
	}

	@Test
	void usesTheItemOnTheCenterOfTheBlockFaceBeforeSwinging() {
		RecordingProtocol protocol = new RecordingProtocol(INTERACTION_PACKETS);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(protocol), operations);

		assertNull(operations.execute(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.WEST, Hand.MAIN)));

		assertEquals(List.of(
				ServerboundUseItemOn.builder()
						.hand(InteractionHand.MAIN_HAND)
						.position(BlockPosition.builder().x(4).y(-60).z(-7).build())
						.face(Direction.WEST)
						.cursorX(0.5F)
						.cursorY(0.5F)
						.cursorZ(0.5F)
						.insideBlock(false)
						.worldBorderHit(false)
						.sequence(1)
						.build(),
				new ServerboundSwing(InteractionHand.MAIN_HAND)
		), protocol.sent);
	}

	@Test
	void sendsEachBlockFaceAsItsDirection() {
		RecordingProtocol protocol = new RecordingProtocol(INTERACTION_PACKETS);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(protocol), operations);

		for (BlockFace face : BlockFace.values())
			operations.execute(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, face, Hand.MAIN));

		assertEquals(List.of(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST),
				protocol.sent.stream()
						.filter(ServerboundUseItemOn.class::isInstance)
						.map(packet -> ((ServerboundUseItemOn) packet).getFace())
						.toList());
	}

	@Test
	void attacksAndInteractsWithEntitiesBeforeSwingingWithoutTakingASequence() {
		RecordingProtocol protocol = new RecordingProtocol(INTERACTION_PACKETS);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(protocol), operations);

		assertNull(operations.execute(InteractionChannel.ENTITY, new EntityUse(42, EntityInteraction.ATTACK, Hand.MAIN)));
		operations.execute(InteractionChannel.ENTITY, new EntityUse(43, EntityInteraction.INTERACT, Hand.OFF));
		operations.execute(InteractionChannel.ITEM, new ItemUse(Hand.MAIN));

		assertEquals(List.of(
				new ServerboundAttack(42),
				new ServerboundSwing(InteractionHand.MAIN_HAND),
				ServerboundInteract.builder().entityId(43).action(InteractAction.INTERACT).hand(InteractionHand.OFF_HAND).build(),
				new ServerboundSwing(InteractionHand.OFF_HAND),
				new ServerboundSwing(InteractionHand.MAIN_HAND),
				ServerboundUseItem.builder().hand(InteractionHand.MAIN_HAND).sequence(1).build()
		), protocol.sent);
	}

	@Test
	void followsAnAttackWithAPunchAndSendsNoSwingWhereTheServerPlaysIt() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of(
				ServerboundUseItem.class,
				ServerboundUseItemOn.class,
				ServerboundInteract.class,
				ServerboundAttack.class,
				ServerboundPunch.class
		));
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(protocol), operations);

		operations.execute(InteractionChannel.ENTITY, new EntityUse(42, EntityInteraction.ATTACK, Hand.MAIN));
		operations.execute(InteractionChannel.ENTITY, new EntityUse(43, EntityInteraction.INTERACT, Hand.OFF));
		operations.execute(InteractionChannel.ITEM, new ItemUse(Hand.MAIN));

		assertEquals(List.of(
				new ServerboundAttack(42),
				new ServerboundPunch(),
				ServerboundInteract.builder().entityId(43).action(InteractAction.INTERACT).hand(InteractionHand.OFF_HAND).build(),
				ServerboundUseItem.builder().hand(InteractionHand.MAIN_HAND).sequence(1).build()
		), protocol.sent);
	}

	@Test
	void numbersItemAndBlockUsesInOneSequencePerPlayer() {
		RecordingProtocol alice = new RecordingProtocol(INTERACTION_PACKETS);
		RecordingProtocol bob = new RecordingProtocol(INTERACTION_PACKETS);
		RecordingOperations aliceOperations = new RecordingOperations();
		RecordingOperations bobOperations = new RecordingOperations();
		extension.bind(new RecordingPlayer(alice), aliceOperations);
		extension.bind(new RecordingPlayer(bob), bobOperations);

		aliceOperations.execute(InteractionChannel.ITEM, new ItemUse(Hand.MAIN));
		aliceOperations.execute(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN));
		bobOperations.execute(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN));
		aliceOperations.execute(InteractionChannel.ITEM, new ItemUse(Hand.MAIN));

		assertEquals(List.of(1, 2, 3), alice.sequences());
		assertEquals(List.of(1), bob.sequences());
	}

	@Test
	void sendsNothingAndTakesNoSequenceWhileThePlayerIsDisconnected() {
		RecordingProtocol protocol = new RecordingProtocol(INTERACTION_PACKETS);
		protocol.connected = false;
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(protocol), operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.execute(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN)));
		assertThrows(IllegalStateException.class,
				() -> operations.execute(InteractionChannel.ITEM, new ItemUse(Hand.MAIN)));
		assertThrows(IllegalStateException.class,
				() -> operations.execute(InteractionChannel.ENTITY,
						new EntityUse(42, EntityInteraction.INTERACT, Hand.MAIN)));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertEquals(List.of(), protocol.sent);

		protocol.connected = true;
		operations.execute(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN));
		assertEquals(List.of(1), protocol.sequences());
	}

	@Test
	void swingsItsOwnArmWhenTheServerSwingsItAndNotForOtherEntities() {
		RecordingProtocol protocol = new RecordingProtocol(INTERACTION_PACKETS);
		extension.bind(new RecordingPlayer(protocol), new RecordingOperations());

		protocol.deliver(new ClientboundSwingAnimation(7, InteractionHand.MAIN_HAND));
		protocol.deliver(new ClientboundLogin(-7, OVERWORLD));
		protocol.deliver(new ClientboundSwingAnimation(-7, InteractionHand.OFF_HAND));
		protocol.deliver(new ClientboundSwingAnimation(7, InteractionHand.MAIN_HAND));

		assertEquals(List.of(new ServerboundSwing(InteractionHand.OFF_HAND)), protocol.sent);
	}

	@Test
	void tellsNoSwingWhereTheServerPlaysIt() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of(ServerboundUseItem.class));
		extension.bind(new RecordingPlayer(protocol), new RecordingOperations());

		protocol.deliver(new ClientboundLogin(7, OVERWORLD));
		protocol.deliver(new ClientboundSwingAnimation(7, InteractionHand.MAIN_HAND));

		assertEquals(List.of(), protocol.sent);
	}

	@Test
	void stopsListeningWhenClosed() {
		RecordingProtocol protocol = new RecordingProtocol(INTERACTION_PACKETS);
		WorkerBinding binding = extension.bind(new RecordingPlayer(protocol), new RecordingOperations());

		binding.close();

		assertEquals(Map.of(), protocol.listeners);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingOperations operations = new RecordingOperations();

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(new RecordingPlayer(null), operations));
		assertEquals(Map.of(), operations.handlers);
	}

	/**
	 * Records the packets sent while connected and refuses them otherwise; it carries the given packets and hands each
	 * delivered packet to the listener of its class.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final Set<Class<? extends JavaPacket>> supported;
		private final List<ServerboundPacket> sent = new ArrayList<>();
		private final Map<Class<?>, Consumer<ClientboundPacket>> listeners = new LinkedHashMap<>();
		private boolean connected = true;

		private RecordingProtocol(Set<Class<? extends JavaPacket>> supported) {
			this.supported = supported;
		}

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			listeners.put(type, packet -> listener.accept(type.cast(packet)));
			return () -> listeners.remove(type);
		}

		private void deliver(ClientboundPacket packet) {
			Consumer<ClientboundPacket> listener = listeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			if (!connected) throw new IllegalStateException("Player Alice is not connected");
			if (!supported.contains(packet.getClass())) throw new ProtocolUnavailableException("not carried: " + packet);

			sent.add(packet);
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The interaction binding observes no sent packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return supported.contains(type);
		}

		private List<Integer> sequences() {
			List<Integer> sequences = new ArrayList<>();
			for (ServerboundPacket packet : sent) {
				if (packet instanceof ServerboundUseItem use) sequences.add(use.getSequence());
				if (packet instanceof ServerboundUseItemOn use) sequences.add(use.getSequence());
			}

			return sequences;
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
	 * A player that speaks the given protocol, or none, with the view rotation interactions share.
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
			throw new AssertionError("Interaction uses the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("Interaction uses the protocol, not the native session");
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
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			throw new AssertionError("Interaction emits no events");
		}
	}
}
