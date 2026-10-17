package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.EntityItem;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.packet.model.EntitiesRemoved;
import me.whereareiam.anvil.capability.entities.packet.model.EntityDataChanged;
import me.whereareiam.anvil.capability.entities.packet.model.EntityMoved;
import me.whereareiam.anvil.capability.entities.packet.model.EntitySpawned;
import me.whereareiam.anvil.capability.entities.packet.model.EntityTurned;
import me.whereareiam.anvil.capability.entities.packet.model.PassengersChanged;
import me.whereareiam.anvil.capability.entities.packet.model.PlayerJoined;
import me.whereareiam.anvil.capability.entities.packet.model.PlayerMoved;
import me.whereareiam.anvil.capability.entities.packet.model.WorldCleared;
import me.whereareiam.anvil.capability.entities.packet.model.data.CustomName;
import me.whereareiam.anvil.capability.entities.packet.model.data.CustomNameVisible;
import me.whereareiam.anvil.capability.entities.packet.model.data.DisplayText;
import me.whereareiam.anvil.capability.entities.packet.model.data.SharedFlags;
import me.whereareiam.anvil.capability.entities.packet.model.data.ShownItem;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTrackerTest {
	private static final int PLAYER = 42;

	@Test
	void reportsASpawnWithTheConnectionAndTheDistanceToThePlacedPlayer() {
		EntityTracker tracker = new EntityTracker(3);
		tracker.apply(new PlayerJoined(PLAYER));
		assertEquals(List.of(), tracker.apply(PlayerMoved.builder().x(0).y(64).z(0).build()));

		EntityChange change = single(tracker.apply(spawn(7, "minecraft:armor_stand", 3, 68, 0)));

		assertEquals(EntityChangeKind.APPEARED, change.getKind());
		assertNull(change.getPrevious());
		SeenEntity stand = change.getEntity();
		assertEquals(3, stand.getConnection());
		assertEquals(7, stand.getId());
		assertEquals(new UUID(0, 7), stand.getUniqueId());
		assertEquals("minecraft:armor_stand", stand.getType());
		assertEquals(5.0, stand.getPlayerDistance());
		assertEquals(List.of(), stand.getPassengerIds());
		assertFalse(stand.isRidingPlayer());
	}

	@Test
	void measuresNoDistanceBeforeThePlayerIsPlaced() {
		EntityTracker tracker = new EntityTracker(1);

		assertNull(single(tracker.apply(spawn(7, "minecraft:zombie", 3, 68, 0))).getEntity().getPlayerDistance());
	}

	@Test
	void appliesDataAndReportsTheEntityBeforeAndAfter() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.apply(spawn(7, "minecraft:armor_stand", 0, 0, 0));

		EntityChange change = single(tracker.apply(new EntityDataChanged(7, List.of(SharedFlags.of((byte) 0x63),
				new CustomName("Alice: hi"), new CustomNameVisible(true)))));
		EntityChange text = single(tracker.apply(new EntityDataChanged(7, List.of(new DisplayText("hi"), new CustomName(null),
				new ShownItem(new EntityItem(871, 3))))));

		assertEquals(EntityChangeKind.CHANGED, change.getKind());
		assertNull(change.getPrevious().getCustomName());
		assertEquals("Alice: hi", change.getEntity().getCustomName());
		assertTrue(change.getEntity().isCustomNameVisible());
		assertEquals(List.of(true, true, true, true, false, false, false), List.of(change.getEntity().isInvisible(),
				change.getEntity().isGlowing(), change.getEntity().isOnFire(), change.getEntity().isSneaking(),
				change.getEntity().isSprinting(), change.getEntity().isSwimming(), change.getEntity().isGliding()));
		assertEquals("hi", text.getEntity().getText());
		assertNull(text.getEntity().getCustomName());
		assertEquals(new EntityItem(871, 3), text.getEntity().getItem());
	}

	@Test
	void reportsNothingForAnUnchangedOrUnknownEntity() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.apply(spawn(7, "minecraft:armor_stand", 0, 0, 0));

		assertEquals(List.of(), tracker.apply(new EntityDataChanged(7, List.of(SharedFlags.of((byte) 0)))));
		assertEquals(List.of(), tracker.apply(new EntityDataChanged(8, List.of(SharedFlags.of((byte) 0x20)))));
		assertEquals(List.of(), tracker.apply(EntityMoved.builder().entityId(8).x(1).build()));
		assertEquals(List.of(), tracker.apply(EntityTurned.builder().entityId(8).yaw(90).build()));
		assertEquals(List.of(), tracker.apply(new EntitiesRemoved(List.of(8))));
	}

	@Test
	void movesSilentlyByRelativeCoordinatesAndToAbsoluteOnes() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.apply(spawn(7, "minecraft:zombie", 1, 2, 3));

		assertEquals(List.of(), tracker.apply(EntityMoved.builder().entityId(7).x(0.5).y(-1).z(0)
				.relativeX(true).relativeY(true).relativeZ(true).build()));
		SeenEntity moved = tracker.known().getFirst();
		assertEquals(List.of(), tracker.apply(EntityMoved.builder().entityId(7).x(10).y(1).z(20).relativeY(true).build()));
		SeenEntity teleported = tracker.known().getFirst();

		assertEquals(List.of(1.5, 1.0, 3.0), List.of(moved.getX(), moved.getY(), moved.getZ()));
		assertEquals(List.of(10.0, 2.0, 20.0), List.of(teleported.getX(), teleported.getY(), teleported.getZ()));
	}

	@Test
	void measuresTheDistanceToWhereThePlayerIsWhenRead() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.apply(PlayerMoved.builder().x(0).y(64).z(0).build());
		tracker.apply(spawn(7, "minecraft:zombie", 0, 64, 10));

		assertEquals(List.of(), tracker.apply(PlayerMoved.builder().z(4).relativeX(true).relativeY(true).relativeZ(true).build()));
		assertEquals(6.0, tracker.known().getFirst().getPlayerDistance());
		tracker.apply(EntityMoved.builder().entityId(7).z(-3).relativeX(true).relativeY(true).relativeZ(true).build());
		assertEquals(3.0, tracker.known().getFirst().getPlayerDistance());
	}

	@Test
	void carriesTheCurrentPositionInTheNextFact() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.apply(spawn(7, "minecraft:armor_stand", 0, 0, 0));
		tracker.apply(EntityMoved.builder().entityId(7).x(5).build());

		EntityChange named = single(tracker.apply(new EntityDataChanged(7, List.of(new CustomName("hello")))));

		assertEquals(5.0, named.getEntity().getX());
		assertEquals(5.0, named.getPrevious().getX());
	}

	@Test
	void keepsTheSpawnRotationAndTurnsSilentlyByRelativeAnglesAndToAbsoluteOnes() {
		EntityTracker tracker = new EntityTracker(1);
		SeenEntity spawned = single(tracker.apply(EntitySpawned.builder().entityId(7).type("minecraft:zombie").yaw(90).pitch(10).build()))
				.getEntity();

		assertEquals(List.of(), tracker.apply(EntityTurned.builder().entityId(7).yaw(180).pitch(5).relativePitch(true).build()));
		SeenEntity turned = tracker.known().getFirst();

		assertEquals(List.of(90f, 10f), List.of(spawned.getYaw(), spawned.getPitch()));
		assertEquals(List.of(180f, 15f), List.of(turned.getYaw(), turned.getPitch()));
	}

	@Test
	void tracksWhatRidesThePlayerThroughEveryVehicle() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.apply(new PlayerJoined(PLAYER));
		tracker.apply(spawn(7, "minecraft:armor_stand", 0, 0, 0));
		tracker.apply(spawn(8, "minecraft:text_display", 0, 0, 0));

		List<EntityChange> onTop = tracker.apply(new PassengersChanged(7, List.of(8)));
		List<EntityChange> mounted = tracker.apply(new PassengersChanged(PLAYER, List.of(7)));

		assertEquals(List.of(8), onTop.get(0).getEntity().getPassengerIds());
		assertEquals(7, onTop.get(1).getEntity().getVehicleId());
		assertFalse(onTop.get(1).getEntity().isRidingPlayer());
		assertEquals(2, mounted.size());
		assertTrue(mounted.stream().allMatch(change -> change.getEntity().isRidingPlayer()));
		assertEquals(PLAYER, mounted.get(0).getEntity().getVehicleId());

		List<EntityChange> dismounted = tracker.apply(new PassengersChanged(PLAYER, List.of()));
		assertEquals(2, dismounted.size());
		assertTrue(dismounted.stream().noneMatch(change -> change.getEntity().isRidingPlayer()));
		assertNull(dismounted.get(0).getEntity().getVehicleId());
	}

	@Test
	void removesEntitiesAndDismountsTheirPassengers() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.apply(new PlayerJoined(PLAYER));
		tracker.apply(spawn(7, "minecraft:armor_stand", 0, 0, 0));
		tracker.apply(spawn(8, "minecraft:text_display", 0, 0, 0));
		tracker.apply(new PassengersChanged(PLAYER, List.of(7)));
		tracker.apply(new PassengersChanged(7, List.of(8)));

		List<EntityChange> changes = tracker.apply(new EntitiesRemoved(List.of(7)));

		assertEquals(EntityChangeKind.DISAPPEARED, changes.get(0).getKind());
		assertEquals(7, changes.get(0).getEntity().getId());
		assertEquals(EntityChangeKind.CHANGED, changes.get(1).getKind());
		assertNull(changes.get(1).getEntity().getVehicleId());
		assertFalse(changes.get(1).getEntity().isRidingPlayer());
	}

	@Test
	void replacesAnEntitySpawnedAgainUnderItsId() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.apply(spawn(7, "minecraft:armor_stand", 0, 0, 0));

		List<EntityChange> changes = tracker.apply(spawn(7, "minecraft:zombie", 0, 0, 0));

		assertEquals(List.of(EntityChangeKind.DISAPPEARED, EntityChangeKind.APPEARED), changes.stream().map(EntityChange::getKind).toList());
		assertEquals("minecraft:zombie", changes.get(1).getEntity().getType());
	}

	@Test
	void forgetsEveryEntityWhenThePlayerJoinsAgainOrTheWorldIsCleared() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.apply(spawn(7, "minecraft:armor_stand", 0, 0, 0));
		tracker.apply(spawn(8, "minecraft:zombie", 0, 0, 0));

		List<EntityChange> joined = tracker.apply(new PlayerJoined(PLAYER));
		tracker.apply(spawn(9, "minecraft:zombie", 0, 0, 0));
		List<EntityChange> cleared = tracker.apply(new WorldCleared());

		assertEquals(List.of(7, 8), joined.stream().map(change -> change.getEntity().getId()).toList());
		assertTrue(joined.stream().allMatch(change -> change.getKind() == EntityChangeKind.DISAPPEARED));
		assertEquals(List.of(9), cleared.stream().map(change -> change.getEntity().getId()).toList());
		assertEquals(List.of(), tracker.apply(new WorldCleared()));
	}

	private static EntitySpawned spawn(int entityId, String type, double x, double y, double z) {
		return EntitySpawned.builder().entityId(entityId).uniqueId(new UUID(0, entityId)).type(type).x(x).y(y).z(z).build();
	}

	private static EntityChange single(List<EntityChange> changes) {
		assertEquals(1, changes.size(), changes::toString);
		return changes.getFirst();
	}
}
