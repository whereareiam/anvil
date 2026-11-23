package me.whereareiam.anvil.capability.entities.java;

import me.whereareiam.anvil.capability.entities.channel.EntitiesChannel;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.KnownEntities;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.edition.java.protocol.model.entity.CustomName;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityTypeKey;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundEntityPositionSync;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityPos;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityRot;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundTeleportEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import me.whereareiam.anvil.player.worker.TestOperations;
import me.whereareiam.anvil.player.worker.TestWorkerPlayer;
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

		assertEquals(List.of(1, 1, 2, 2), changes().stream().map(change -> change.entity().connection()).toList());
		assertEquals(List.of(EntityChangeKind.APPEARED, EntityChangeKind.DISAPPEARED, EntityChangeKind.APPEARED,
				EntityChangeKind.DISAPPEARED), changes().stream().map(EntityChange::kind).toList());
	}

	@Test
	void readsTheEntitiesOfTheCurrentConnectionWithoutEmittingMoves() {
		extension.bind(player, operations);
		assertEquals(new KnownEntities(List.of()), operations.call(EntitiesChannel.KNOWN, null));

		player.connect();
		player.deliver(spawn(7, "zombie", 0, 0, 0));
		player.rejoin();
		player.deliver(spawn(8, "zombie", 0, 0, 0));
		player.deliver(ClientboundMoveEntityPos.builder().entityId(new EntityId(8)).delta(new Vec3d(4, 0, 0)).build());

		List<SeenEntity> known = operations.call(EntitiesChannel.KNOWN, null).entities();
		assertEquals(List.of("entities.known"), operations.registered());
		assertEquals(List.of(8), known.stream().map(SeenEntity::id).toList());
		assertEquals(4.0, known.getFirst().x());
		assertEquals(2, known.getFirst().connection());
		assertEquals(3, changes().size());
	}

	@Test
	void spawnsWhatTheAddedEntitiesSay() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(spawn(7, "armor_stand", 1.5, 64, -2.5));
		player.deliver(ClientboundAddEntity.builder().entityId(new EntityId(8)).uniqueId(new UUID(0, 8))
				.type(EntityTypeKey.minecraft("zombie")).position(new Vec3d(0, 66, 0)).yaw(180).pitch(20).build());
		player.deliver(ClientboundAddEntity.builder().entityId(new EntityId(9)).uniqueId(new UUID(0, 9))
				.type(EntityTypeKey.minecraft("player")).position(new Vec3d(2, 64, 2)).yaw(270).pitch(-30).build());
		player.deliver(ClientboundAddEntity.builder().entityId(new EntityId(10)).uniqueId(new UUID(0, 10))
				.type(EntityTypeKey.minecraft("painting")).position(new Vec3d(4, 65, 4)).build());
		player.deliver(ClientboundAddEntity.builder().entityId(new EntityId(11)).type(EntityTypeKey.minecraft("experience_orb"))
				.position(new Vec3d(1, 2, 3)).build());

		List<SeenEntity> spawned = changes().stream().map(EntityChange::entity).toList();
		assertEquals(List.of("minecraft:armor_stand", "minecraft:zombie", "minecraft:player", "minecraft:painting",
				"minecraft:experience_orb"), spawned.stream().map(SeenEntity::type).toList());
		assertEquals(List.of(1.5, 64.0, -2.5), List.of(spawned.get(0).x(), spawned.get(0).y(), spawned.get(0).z()));
		assertEquals(List.of(180f, 20f), List.of(spawned.get(1).yaw(), spawned.get(1).pitch()));
		assertEquals(new UUID(0, 9), spawned.get(2).uniqueId());
		assertEquals(List.of(4.0, 65.0, 4.0), List.of(spawned.get(3).x(), spawned.get(3).y(), spawned.get(3).z()));
		assertNull(spawned.get(4).uniqueId());
	}

	@Test
	void appliesDataPassengersOfThePlayerAndRemovals() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(login(PLAYER));
		player.deliver(spawn(7, "armor_stand", 0, 0, 0));
		player.deliver(new ClientboundSetEntityData(new EntityId(7), List.of(new CustomName("Alice: hello"))));
		player.deliver(new ClientboundSetPassengers(new EntityId(PLAYER), List.of(new EntityId(7))));
		player.deliver(new ClientboundRemoveEntities(List.of(new EntityId(7))));

		assertEquals(List.of(EntityChangeKind.APPEARED, EntityChangeKind.CHANGED, EntityChangeKind.CHANGED,
				EntityChangeKind.DISAPPEARED), changes().stream().map(EntityChange::kind).toList());
		assertEquals("Alice: hello", changes().get(1).entity().customName());
		assertTrue(changes().get(2).entity().ridingPlayer());
	}

	@Test
	void movesAndTurnsEntitiesByEachPlacementPacket() {
		extension.bind(player, operations);
		player.connect();
		player.deliver(spawn(7, "zombie", 1, 2, 3));

		player.deliver(ClientboundMoveEntityPosRot.builder().entityId(new EntityId(7)).delta(new Vec3d(1, 0, 0)).yaw(90).pitch(10)
				.build());
		assertEquals(List.of(2.0, 2.0, 3.0, 90f, 10f), place());
		player.deliver(new ClientboundMoveEntityRot(new EntityId(7), 180, -10));
		assertEquals(List.of(2.0, 2.0, 3.0, 180f, -10f), place());
		player.deliver(ClientboundTeleportEntity.builder().entityId(new EntityId(7)).position(new Vec3d(1, 2, 3)).yaw(15)
				.pitch(5).relatives(Set.of(Relative.Y, Relative.YAW)).build());
		assertEquals(List.of(1.0, 4.0, 3.0, 195f, 5f), place());
		player.deliver(ClientboundEntityPositionSync.builder().entityId(new EntityId(7)).position(new Vec3d(4, 5, 6)).yaw(30)
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
				EntityChangeKind.DISAPPEARED), changes().stream().map(EntityChange::kind).toList());
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
				.entityId(new EntityId(entityId))
				.uniqueId(new UUID(0, entityId))
				.type(EntityTypeKey.minecraft(type))
				.position(new Vec3d(x, y, z))
				.build();
	}

	private List<Object> place() {
		SeenEntity entity = operations.call(EntitiesChannel.KNOWN, null).entities().getFirst();
		return List.of(entity.x(), entity.y(), entity.z(), entity.yaw(), entity.pitch());
	}

	private @Nullable Double distance() {
		return operations.call(EntitiesChannel.KNOWN, null).entities().getFirst().playerDistance();
	}

	private static ClientboundLogin login(int entityId) {
		return ClientboundLogin.builder()
				.entityId(new EntityId(entityId))
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
