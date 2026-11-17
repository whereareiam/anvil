package me.whereareiam.anvil.capability.entities.java;

import me.whereareiam.anvil.capability.entities.channel.EntitiesChannel;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.KnownEntities;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundEntityPositionSync;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundMoveEntityRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
import me.whereareiam.anvil.protocol.java.worker.TestJavaPlayer;
import me.whereareiam.anvil.protocol.worker.TestOperations;
import me.whereareiam.anvil.protocol.worker.TestWorkerPlayer;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaEntitiesExtensionTest {
	private static final int PLAYER = 42;
	private static final List<Class<? extends ClientboundPacket>> LISTENED = List.of(ClientboundLogin.class,
			ClientboundRespawn.class, ClientboundAddEntity.class, ClientboundMoveEntityPos.class,
			ClientboundMoveEntityPosRot.class, ClientboundMoveEntityRot.class, ClientboundTeleportEntity.class,
			ClientboundPlayerPosition.class, ClientboundSetEntityData.class, ClientboundSetPassengers.class,
			ClientboundRemoveEntities.class);

	private final JavaEntitiesExtension extension = new JavaEntitiesExtension();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();

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
		extension.bind(player, operations);

		player.connect();
		player.deliver(spawn(7, "armor_stand", 0, 0, 0));
		player.rejoin();
		player.deliver(spawn(7, "zombie", 0, 0, 0));
		player.deliver(respawn());

		assertEquals(List.of(1, 1, 2, 2), changes().stream().map(change -> change.getEntity().getConnection()).toList());
		assertEquals(List.of(EntityChangeKind.APPEARED, EntityChangeKind.DISAPPEARED, EntityChangeKind.APPEARED,
				EntityChangeKind.DISAPPEARED), changes().stream().map(EntityChange::getKind).toList());
	}

	@Test
	void readsTheEntitiesOfTheCurrentConnectionWithoutEmittingMoves() {
		extension.bind(player, operations);
		assertEquals(new KnownEntities(List.of()), operations.call(EntitiesChannel.KNOWN, null));

		player.connect();
		player.deliver(spawn(7, "zombie", 0, 0, 0));
		player.rejoin();
		player.deliver(spawn(8, "zombie", 0, 0, 0));
		player.deliver(ClientboundMoveEntityPos.builder().entityId(8).delta(new Vec3d(4, 0, 0)).build());

		List<SeenEntity> known = operations.call(EntitiesChannel.KNOWN, null).getEntities();
		assertEquals(List.of("entities.known"), operations.registered());
		assertEquals(List.of(8), known.stream().map(SeenEntity::getId).toList());
		assertEquals(4.0, known.getFirst().getX());
		assertEquals(2, known.getFirst().getConnection());
		assertEquals(3, changes().size());
	}

	@Test
	void spawnsWhatTheAddedEntitiesSay() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(spawn(7, "armor_stand", 1.5, 64, -2.5));
		player.deliver(ClientboundAddEntity.builder().entityId(8).uniqueId(new UUID(0, 8))
				.type(EntityTypeKey.minecraft("zombie")).position(new Vec3d(0, 66, 0)).yaw(180).pitch(20).build());
		player.deliver(ClientboundAddEntity.builder().entityId(9).uniqueId(new UUID(0, 9))
				.type(EntityTypeKey.minecraft("player")).position(new Vec3d(2, 64, 2)).yaw(270).pitch(-30).build());
		player.deliver(ClientboundAddEntity.builder().entityId(10).uniqueId(new UUID(0, 10))
				.type(EntityTypeKey.minecraft("painting")).position(new Vec3d(4, 65, 4)).build());
		player.deliver(ClientboundAddEntity.builder().entityId(11).type(EntityTypeKey.minecraft("experience_orb"))
				.position(new Vec3d(1, 2, 3)).build());

		List<SeenEntity> spawned = changes().stream().map(EntityChange::getEntity).toList();
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
		extension.bind(player, operations);
		player.connect();

		player.deliver(login(PLAYER));
		player.deliver(spawn(7, "armor_stand", 0, 0, 0));
		player.deliver(new ClientboundSetEntityData(7, List.of(new CustomName("Alice: hello"))));
		player.deliver(new ClientboundSetPassengers(PLAYER, List.of(7)));
		player.deliver(new ClientboundRemoveEntities(List.of(7)));

		assertEquals(List.of(EntityChangeKind.APPEARED, EntityChangeKind.CHANGED, EntityChangeKind.CHANGED,
				EntityChangeKind.DISAPPEARED), changes().stream().map(EntityChange::getKind).toList());
		assertEquals("Alice: hello", changes().get(1).getEntity().getCustomName());
		assertTrue(changes().get(2).getEntity().isRidingPlayer());
	}

	@Test
	void movesAndTurnsEntitiesByEachPlacementPacket() {
		extension.bind(player, operations);
		player.connect();
		player.deliver(spawn(7, "zombie", 1, 2, 3));

		player.deliver(ClientboundMoveEntityPosRot.builder().entityId(7).delta(new Vec3d(1, 0, 0)).yaw(90).pitch(10)
				.build());
		assertEquals(List.of(2.0, 2.0, 3.0, 90f, 10f), place());
		player.deliver(new ClientboundMoveEntityRot(7, 180, -10));
		assertEquals(List.of(2.0, 2.0, 3.0, 180f, -10f), place());
		player.deliver(ClientboundTeleportEntity.builder().entityId(7).position(new Vec3d(1, 2, 3)).yaw(15)
				.pitch(5).relatives(Set.of(Relative.Y, Relative.YAW)).build());
		assertEquals(List.of(1.0, 4.0, 3.0, 195f, 5f), place());
		player.deliver(ClientboundEntityPositionSync.builder().entityId(7).position(new Vec3d(4, 5, 6)).yaw(30)
				.pitch(20).build());
		assertEquals(List.of(4.0, 5.0, 6.0, 30f, 20f), place());
		assertEquals(1, changes().size());
	}

	@Test
	void measuresDistancesFromWhereTheServerPlacedThePlayerAndWhereItMovedItself() {
		extension.bind(player, operations);
		player.connect();
		player.deliver(spawn(7, "zombie", 0, 64, 10));

		player.deliver(ClientboundPlayerPosition.builder().position(new Vec3d(0, 64, 4)).relatives(Set.of()).build());
		assertEquals(6.0, distance());
		player.deliver(ClientboundPlayerPosition.builder().position(new Vec3d(0, 0, 2))
				.relatives(Set.of(Relative.X, Relative.Y, Relative.Z))
				.build());
		assertEquals(4.0, distance());
		player.protocol().send(ServerboundMovePlayerPosRot.builder().position(new Vec3d(0, 64, 7)).build());
		assertEquals(3.0, distance());
	}

	@Test
	void forgetsEveryEntityWhenThePlayerJoinsALevelOrReturnsToConfiguration() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(spawn(7, "zombie", 0, 0, 0));
		player.deliver(login(PLAYER));
		player.deliver(spawn(8, "zombie", 0, 0, 0));
		player.deliver(new ClientboundStartConfiguration());

		assertEquals(List.of(EntityChangeKind.APPEARED, EntityChangeKind.DISAPPEARED, EntityChangeKind.APPEARED,
				EntityChangeKind.DISAPPEARED), changes().stream().map(EntityChange::getKind).toList());
	}

	@Test
	void listensOnlyToThePacketsTheReleaseCarries() {
		TestJavaPlayer older = new TestJavaPlayer("Alice", type -> type != ClientboundEntityPositionSync.class
				&& type != ClientboundStartConfiguration.class);
		extension.bind(older, operations);

		for (Class<? extends ClientboundPacket> type : LISTENED)
			assertTrue(older.protocol().wants(type), type.getSimpleName());
		assertFalse(older.protocol().wants(ClientboundEntityPositionSync.class));
		assertFalse(older.protocol().wants(ClientboundStartConfiguration.class));
	}

	@Test
	void stopsListeningAndEmitsNothingMoreWhenClosed() {
		WorkerBinding binding = extension.bind(player, operations);
		player.connect();
		player.deliver(spawn(7, "zombie", 0, 0, 0));

		binding.close();
		player.deliver(spawn(8, "zombie", 0, 0, 0));
		player.protocol().send(ServerboundMovePlayerPosRot.builder().position(new Vec3d(0, 64, 7)).build());
		player.rejoin();
		player.deliver(spawn(9, "zombie", 0, 0, 0));

		for (Class<? extends ClientboundPacket> type : LISTENED)
			assertFalse(player.protocol().wants(type), type.getSimpleName());
		assertFalse(player.protocol().wants(ClientboundEntityPositionSync.class));
		assertFalse(player.protocol().wants(ClientboundStartConfiguration.class));
		assertEquals(1, changes().size());
		assertEquals(new KnownEntities(List.of()), operations.call(EntitiesChannel.KNOWN, null));
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		assertEquals(List.of(), operations.registered());
	}

	private List<EntityChange> changes() {
		return player.emitted(EntitiesChannel.CHANGED);
	}

	private static ClientboundAddEntity spawn(int entityId, String type, double x, double y, double z) {
		return ClientboundAddEntity.builder()
				.entityId(entityId)
				.uniqueId(new UUID(0, entityId))
				.type(EntityTypeKey.minecraft(type))
				.position(new Vec3d(x, y, z))
				.build();
	}

	private List<Object> place() {
		SeenEntity entity = operations.call(EntitiesChannel.KNOWN, null).getEntities().getFirst();
		return List.of(entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), entity.getPitch());
	}

	private @Nullable Double distance() {
		return operations.call(EntitiesChannel.KNOWN, null).getEntities().getFirst().getPlayerDistance();
	}

	private static ClientboundLogin login(int entityId) {
		return ClientboundLogin.builder()
				.entityId(entityId)
				.level("minecraft:overworld")
				.dimension("minecraft:overworld")
				.dimensionType(DimensionType.known("minecraft:overworld"))
				.build();
	}

	private static ClientboundRespawn respawn() {
		return ClientboundRespawn.builder()
				.dimension("minecraft:overworld")
				.dimensionType(DimensionType.known("minecraft:overworld"))
				.build();
	}
}
