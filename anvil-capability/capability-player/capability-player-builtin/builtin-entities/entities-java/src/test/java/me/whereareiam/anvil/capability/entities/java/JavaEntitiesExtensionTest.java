package me.whereareiam.anvil.capability.entities.java;

import me.whereareiam.anvil.capability.entities.channel.EntitiesChannel;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.KnownEntities;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
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
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityPosition;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.interaction.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.player.Position;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddExperienceOrb;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddMob;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddPainting;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddPlayer;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundEntityPositionSync;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaEntitiesExtensionTest {
	private static final int PLAYER = 42;

	private final JavaEntitiesExtension extension = new JavaEntitiesExtension();

	@Test
	void isDiscoveredAsTheEditionWideEntitiesExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaEntitiesExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.entities", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void emitsTheFactsOfEachConnectionNumberedByConnectionAndForgetsTheEntitiesOfAnEndedOne() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of());
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		player.open();
		protocol.deliver(spawn(7, "armor_stand", 0, 0, 0));
		player.open();
		protocol.deliver(spawn(7, "zombie", 0, 0, 0));
		protocol.deliver(new ClientboundRespawn());

		assertEquals(List.of(1, 1, 2, 2), player.emitted.stream().map(change -> change.getEntity().getConnection()).toList());
		assertEquals(List.of(EntityChangeKind.APPEARED, EntityChangeKind.DISAPPEARED, EntityChangeKind.APPEARED,
				EntityChangeKind.DISAPPEARED), player.emitted.stream().map(EntityChange::getKind).toList());
	}

	@Test
	void readsTheEntitiesOfTheCurrentConnectionWithoutEmittingMoves() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of());
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		assertEquals(new KnownEntities(List.of()), operations.execute(EntitiesChannel.KNOWN, null));

		player.open();
		protocol.deliver(spawn(7, "zombie", 0, 0, 0));
		player.open();
		protocol.deliver(spawn(8, "zombie", 0, 0, 0));
		protocol.deliver(ClientboundMoveEntityPos.builder().entityId(8).deltaX(4).build());

		List<SeenEntity> known = operations.execute(EntitiesChannel.KNOWN, null).getEntities();
		assertEquals(List.of("entities.known"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of(8), known.stream().map(SeenEntity::getId).toList());
		assertEquals(4.0, known.getFirst().getX());
		assertEquals(2, known.getFirst().getConnection());
		assertEquals(3, player.emitted.size());
	}

	@Test
	void decodesWhatTheSpawnPacketsOfEveryReleaseSay() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of());
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());
		player.open();

		protocol.deliver(spawn(7, "armor_stand", 1.5, 64, -2.5));
		protocol.deliver(ClientboundAddMob.builder().entityId(8).uniqueId(new UUID(0, 8)).type(EntityTypeKey.minecraft("zombie"))
				.position(new EntityPosition(0, 66, 0)).yaw(180).pitch(20).build());
		protocol.deliver(ClientboundAddPlayer.builder().entityId(9).uniqueId(new UUID(0, 9))
				.position(new EntityPosition(2, 64, 2)).yaw(270).pitch(-30).build());
		protocol.deliver(ClientboundAddPainting.builder().entityId(10).uniqueId(new UUID(0, 10))
				.position(BlockPosition.builder().x(4).y(65).z(4).build()).build());
		protocol.deliver(ClientboundAddExperienceOrb.builder().entityId(11).position(new EntityPosition(1, 2, 3)).build());

		List<SeenEntity> spawned = player.emitted.stream().map(EntityChange::getEntity).toList();
		assertEquals(List.of("minecraft:armor_stand", "minecraft:zombie", "minecraft:player", "minecraft:painting",
				"minecraft:experience_orb"), spawned.stream().map(SeenEntity::getType).toList());
		assertEquals(List.of(1.5, 64.0, -2.5), List.of(spawned.get(0).getX(), spawned.get(0).getY(), spawned.get(0).getZ()));
		assertEquals(List.of(180f, 20f), List.of(spawned.get(1).getYaw(), spawned.get(1).getPitch()));
		assertEquals(new UUID(0, 9), spawned.get(2).getUniqueId());
		assertEquals(List.of(4.0, 65.0, 4.0), List.of(spawned.get(3).getX(), spawned.get(3).getY(), spawned.get(3).getZ()));
		assertNull(spawned.get(4).getUniqueId());
	}

	@Test
	void appliesDataPassengersOfThePlayerAndRemovals() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of());
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());
		player.open();

		protocol.deliver(new ClientboundLogin(PLAYER));
		protocol.deliver(spawn(7, "armor_stand", 0, 0, 0));
		protocol.deliver(new ClientboundSetEntityData(7, List.of(new CustomName("Alice: hello"))));
		protocol.deliver(new ClientboundSetPassengers(PLAYER, List.of(7)));
		protocol.deliver(new ClientboundRemoveEntities(List.of(7)));

		assertEquals(List.of(EntityChangeKind.APPEARED, EntityChangeKind.CHANGED, EntityChangeKind.CHANGED,
				EntityChangeKind.DISAPPEARED), player.emitted.stream().map(EntityChange::getKind).toList());
		assertEquals("Alice: hello", player.emitted.get(1).getEntity().getCustomName());
		assertTrue(player.emitted.get(2).getEntity().isRidingPlayer());
	}

	@Test
	void movesAndTurnsEntitiesByEachPlacementPacket() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of());
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		player.open();
		protocol.deliver(spawn(7, "zombie", 1, 2, 3));

		protocol.deliver(ClientboundMoveEntityPosRot.builder().entityId(7).deltaX(1).yaw(90).pitch(10).build());
		assertEquals(List.of(2.0, 2.0, 3.0, 90f, 10f), place(operations));
		protocol.deliver(new ClientboundMoveEntityRot(7, 180, -10));
		assertEquals(List.of(2.0, 2.0, 3.0, 180f, -10f), place(operations));
		protocol.deliver(ClientboundTeleportEntity.builder().entityId(7).position(new EntityPosition(1, 2, 3)).yaw(15)
				.pitch(5).relatives(Set.of(Relative.Y, Relative.YAW)).build());
		assertEquals(List.of(1.0, 4.0, 3.0, 195f, 5f), place(operations));
		protocol.deliver(ClientboundEntityPositionSync.builder().entityId(7).position(new EntityPosition(4, 5, 6)).yaw(30)
				.pitch(20).build());
		assertEquals(List.of(4.0, 5.0, 6.0, 30f, 20f), place(operations));
		assertEquals(1, player.emitted.size());
	}

	@Test
	void measuresDistancesFromWhereTheServerPlacedThePlayerAndWhereItMovedItself() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of());
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		player.open();
		protocol.deliver(spawn(7, "zombie", 0, 64, 10));

		protocol.deliver(ClientboundPlayerPosition.builder().x(0).y(64).z(4).relatives(Set.of()).build());
		assertEquals(6.0, distance(operations));
		protocol.deliver(ClientboundPlayerPosition.builder().z(2).relatives(Set.of(Relative.X, Relative.Y, Relative.Z))
				.build());
		assertEquals(4.0, distance(operations));
		protocol.send(ServerboundMovePlayerPosRot.builder().position(Position.builder().x(0).y(64).z(7).build()).build());
		assertEquals(3.0, distance(operations));
	}

	@Test
	void forgetsEveryEntityWhenThePlayerJoinsALevelOrReturnsToConfiguration() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of());
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());
		player.open();

		protocol.deliver(spawn(7, "zombie", 0, 0, 0));
		protocol.deliver(new ClientboundLogin(PLAYER));
		protocol.deliver(spawn(8, "zombie", 0, 0, 0));
		protocol.deliver(new ClientboundStartConfiguration());

		assertEquals(List.of(EntityChangeKind.APPEARED, EntityChangeKind.DISAPPEARED, EntityChangeKind.APPEARED,
				EntityChangeKind.DISAPPEARED), player.emitted.stream().map(EntityChange::getKind).toList());
	}

	@Test
	void listensOnlyToThePacketsTheReleaseCarries() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of(ClientboundAddMob.class, ClientboundAddPlayer.class,
				ClientboundAddPainting.class, ClientboundAddExperienceOrb.class, ClientboundEntityPositionSync.class,
				ClientboundStartConfiguration.class));
		extension.bind(new RecordingPlayer(protocol), new RecordingOperations());

		assertEquals(Set.of(ClientboundLogin.class, ClientboundRespawn.class, ClientboundAddEntity.class,
				ClientboundMoveEntityPos.class, ClientboundMoveEntityPosRot.class, ClientboundMoveEntityRot.class,
				ClientboundTeleportEntity.class, ClientboundPlayerPosition.class, ClientboundSetEntityData.class,
				ClientboundSetPassengers.class, ClientboundRemoveEntities.class), protocol.listeners.keySet());
		assertEquals(Set.of(ServerboundMovePlayerPosRot.class), protocol.sentListeners.keySet());
	}

	@Test
	void stopsListeningAndEmitsNothingMoreWhenClosed() {
		RecordingProtocol protocol = new RecordingProtocol(Set.of());
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		WorkerBinding binding = extension.bind(player, operations);
		player.open();
		protocol.deliver(spawn(7, "zombie", 0, 0, 0));

		binding.close();
		protocol.deliver(spawn(8, "zombie", 0, 0, 0));

		assertEquals(Map.of(), protocol.listeners);
		assertEquals(Map.of(), protocol.sentListeners);
		assertFalse(player.bound());
		assertEquals(1, player.emitted.size());
		assertEquals(new KnownEntities(List.of()), operations.execute(EntitiesChannel.KNOWN, null));
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		assertThrows(ProtocolUnavailableException.class,
				() -> extension.bind(new RecordingPlayer(null), new RecordingOperations()));
	}

	private static ClientboundAddEntity spawn(int entityId, String type, double x, double y, double z) {
		return ClientboundAddEntity.builder()
				.entityId(entityId)
				.uniqueId(new UUID(0, entityId))
				.type(EntityTypeKey.minecraft(type))
				.position(new EntityPosition(x, y, z))
				.build();
	}

	private static List<Object> place(RecordingOperations operations) {
		SeenEntity entity = operations.execute(EntitiesChannel.KNOWN, null).getEntities().getFirst();
		return List.of(entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), entity.getPitch());
	}

	private static @Nullable Double distance(RecordingOperations operations) {
		return operations.execute(EntitiesChannel.KNOWN, null).getEntities().getFirst().getPlayerDistance();
	}

	/**
	 * Hands delivered packets to the listener of their class and sent packets to the listeners of sent packets; it
	 * carries every packet but the ones a test leaves out.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final Set<Class<?>> unsupported;
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();
		private final Map<Class<?>, Consumer<Object>> sentListeners = new LinkedHashMap<>();

		private RecordingProtocol(Set<Class<?>> unsupported) {
			this.unsupported = unsupported;
		}

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			if (!supports(type)) throw new ProtocolUnavailableException("not carried: " + type.getName());

			listeners.put(type, packet -> listener.accept(type.cast(packet)));
			return () -> listeners.remove(type);
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			Consumer<Object> listener = sentListeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			sentListeners.put(type, packet -> listener.accept(type.cast(packet)));
			return () -> sentListeners.remove(type);
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return !unsupported.contains(type);
		}

		private void deliver(ClientboundPacket packet) {
			Consumer<Object> listener = listeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), (Function<Object, Object>) handler);
		}

		private <Q, R> R execute(OperationDescriptor<Q, R> operation, @Nullable Q request) {
			return operation.getResponseType().cast(handlers.get(operation.getId()).apply(request));
		}
	}

	/**
	 * A player that speaks the given protocol, or none, whose connections the test opens one after another; opening
	 * a connection ends the previous one.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final List<EntityChange> emitted = new ArrayList<>();
		private @Nullable Function<Object, Subscription> generations;
		private @Nullable Subscription current;

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
		}

		private void open() {
			if (current != null) current.close();
			current = generations.apply(new Object());
		}

		private boolean bound() {
			return generations != null;
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			if (protocol == null) return PlayerBindingContext.super.protocol(type);

			return type.cast(protocol);
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			generations = listener;
			return () -> {
				generations = null;
				if (current != null) current.close();
				current = null;
			};
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			assertSame(EntitiesChannel.CHANGED, event);
			emitted.add((EntityChange) payload);
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
			throw new AssertionError("Entities do not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Entities do not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Entities do not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("Entities use the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("Entities use the protocol, not the native session");
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Entities do not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Entities do not change the view rotation");
		}
	}
}
