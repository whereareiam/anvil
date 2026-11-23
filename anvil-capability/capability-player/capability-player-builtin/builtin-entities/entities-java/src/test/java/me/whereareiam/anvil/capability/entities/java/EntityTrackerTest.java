package me.whereareiam.anvil.capability.entities.java;

import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.EntityItem;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.edition.java.protocol.model.entity.CustomName;
import me.whereareiam.anvil.edition.java.protocol.model.entity.CustomNameVisible;
import me.whereareiam.anvil.edition.java.protocol.model.entity.DisplayText;
import me.whereareiam.anvil.edition.java.protocol.model.entity.SharedFlags;
import me.whereareiam.anvil.edition.java.protocol.model.entity.ShownItem;
import me.whereareiam.anvil.edition.java.protocol.model.inventory.ItemStack;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
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
		tracker.join(PLAYER);
		tracker.movePlayer(0, 64, 0, false, false, false);

		EntityChange change = single(spawn(tracker, 7, "minecraft:armor_stand", 3, 68, 0));

		assertEquals(EntityChangeKind.APPEARED, change.kind());
		assertNull(change.previous());
		SeenEntity stand = change.entity();
		assertEquals(3, stand.connection());
		assertEquals(7, stand.id());
		assertEquals(new UUID(0, 7), stand.uniqueId());
		assertEquals("minecraft:armor_stand", stand.type());
		assertEquals(5.0, stand.playerDistance());
		assertEquals(List.of(), stand.passengerIds());
		assertFalse(stand.ridingPlayer());
	}

	@Test
	void measuresNoDistanceBeforeThePlayerIsPlaced() {
		EntityTracker tracker = new EntityTracker(1);

		assertNull(single(spawn(tracker, 7, "minecraft:zombie", 3, 68, 0)).entity().playerDistance());
	}

	@Test
	void appliesDataAndReportsTheEntityBeforeAndAfter() {
		EntityTracker tracker = new EntityTracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);

		EntityChange change = single(tracker.change(7, List.of(
				SharedFlags.builder().onFire(true).sneaking(true).invisible(true).glowing(true).build(),
				new CustomName("Alice: hi"),
				new CustomNameVisible(true)
		)));
		EntityChange text = single(tracker.change(7, List.of(new DisplayText("hi"), new CustomName(null),
				new ShownItem(ItemStack.of(871, 3)))));

		assertEquals(EntityChangeKind.CHANGED, change.kind());
		assertNull(change.previous().customName());
		assertEquals("Alice: hi", change.entity().customName());
		assertTrue(change.entity().customNameVisible());
		assertEquals(List.of(true, true, true, true, false, false, false), List.of(change.entity().invisible(),
				change.entity().glowing(), change.entity().onFire(), change.entity().sneaking(),
				change.entity().sprinting(), change.entity().swimming(), change.entity().gliding()));
		assertEquals("hi", text.entity().text());
		assertNull(text.entity().customName());
		assertEquals(new EntityItem(871, 3), text.entity().item());
	}

	@Test
	void showsNoItemForAnEmptyStack() {
		EntityTracker tracker = new EntityTracker(1);
		spawn(tracker, 7, "minecraft:item", 0, 0, 0);
		tracker.change(7, List.of(new ShownItem(ItemStack.of(871, 3))));

		assertNull(single(tracker.change(7, List.of(new ShownItem(ItemStack.of(871, 0))))).entity().item());
		assertEquals(List.of(), tracker.change(7, List.of(new ShownItem(null))));
	}

	@Test
	void reportsNothingForAnUnchangedOrUnknownEntity() {
		EntityTracker tracker = new EntityTracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);

		assertEquals(List.of(), tracker.change(7, List.of(SharedFlags.builder().build())));
		assertEquals(List.of(), tracker.change(7, List.of()));
		assertEquals(List.of(), tracker.change(8, List.of(SharedFlags.builder().invisible(true).build())));
		tracker.move(8, 1, 0, 0, false, false, false);
		tracker.turn(8, 90, 0, false, false);
		assertEquals(List.of(7), tracker.known().stream().map(SeenEntity::id).toList());
		assertEquals(List.of(), tracker.remove(List.of(8)));
	}

	@Test
	void movesByRelativeCoordinatesAndToAbsoluteOnes() {
		EntityTracker tracker = new EntityTracker(1);
		spawn(tracker, 7, "minecraft:zombie", 1, 2, 3);

		tracker.move(7, 0.5, -1, 0, true, true, true);
		SeenEntity moved = tracker.known().getFirst();
		tracker.move(7, 10, 1, 20, false, true, false);
		SeenEntity teleported = tracker.known().getFirst();

		assertEquals(List.of(1.5, 1.0, 3.0), List.of(moved.x(), moved.y(), moved.z()));
		assertEquals(List.of(10.0, 2.0, 20.0), List.of(teleported.x(), teleported.y(), teleported.z()));
	}

	@Test
	void measuresTheDistanceToWhereThePlayerIsWhenRead() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.movePlayer(0, 64, 0, false, false, false);
		spawn(tracker, 7, "minecraft:zombie", 0, 64, 10);

		tracker.movePlayer(0, 0, 4, true, true, true);
		assertEquals(6.0, tracker.known().getFirst().playerDistance());
		tracker.move(7, 0, 0, -3, true, true, true);
		assertEquals(3.0, tracker.known().getFirst().playerDistance());
	}

	@Test
	void carriesTheCurrentPositionInTheNextFact() {
		EntityTracker tracker = new EntityTracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);
		tracker.move(7, 5, 0, 0, false, false, false);

		EntityChange named = single(tracker.change(7, List.of(new CustomName("hello"))));

		assertEquals(5.0, named.entity().x());
		assertEquals(5.0, named.previous().x());
	}

	@Test
	void keepsTheSpawnRotationAndTurnsByRelativeAnglesAndToAbsoluteOnes() {
		EntityTracker tracker = new EntityTracker(1);
		SeenEntity spawned = single(tracker.spawn(7, null, "minecraft:zombie", new Vec3d(0, 0, 0), 90, 10))
				.entity();

		tracker.turn(7, 180, 5, false, true);
		SeenEntity turned = tracker.known().getFirst();

		assertNull(spawned.uniqueId());
		assertEquals(List.of(90f, 10f), List.of(spawned.yaw(), spawned.pitch()));
		assertEquals(List.of(180f, 15f), List.of(turned.yaw(), turned.pitch()));
	}

	@Test
	void tracksWhatRidesThePlayerThroughEveryVehicle() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.join(PLAYER);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);
		spawn(tracker, 8, "minecraft:text_display", 0, 0, 0);

		List<EntityChange> onTop = tracker.mount(7, List.of(8));
		List<EntityChange> mounted = tracker.mount(PLAYER, List.of(7));

		assertEquals(List.of(8), onTop.get(0).entity().passengerIds());
		assertEquals(7, onTop.get(1).entity().vehicleId());
		assertFalse(onTop.get(1).entity().ridingPlayer());
		assertEquals(2, mounted.size());
		assertTrue(mounted.stream().allMatch(change -> change.entity().ridingPlayer()));
		assertEquals(PLAYER, mounted.get(0).entity().vehicleId());

		List<EntityChange> dismounted = tracker.mount(PLAYER, List.of());
		assertEquals(2, dismounted.size());
		assertTrue(dismounted.stream().noneMatch(change -> change.entity().ridingPlayer()));
		assertNull(dismounted.get(0).entity().vehicleId());
	}

	@Test
	void removesEntitiesAndDismountsTheirPassengers() {
		EntityTracker tracker = new EntityTracker(1);
		tracker.join(PLAYER);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);
		spawn(tracker, 8, "minecraft:text_display", 0, 0, 0);
		tracker.mount(PLAYER, List.of(7));
		tracker.mount(7, List.of(8));

		List<EntityChange> changes = tracker.remove(List.of(7));

		assertEquals(EntityChangeKind.DISAPPEARED, changes.get(0).kind());
		assertEquals(7, changes.get(0).entity().id());
		assertEquals(EntityChangeKind.CHANGED, changes.get(1).kind());
		assertNull(changes.get(1).entity().vehicleId());
		assertFalse(changes.get(1).entity().ridingPlayer());
	}

	@Test
	void replacesAnEntitySpawnedAgainUnderItsId() {
		EntityTracker tracker = new EntityTracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);

		List<EntityChange> changes = spawn(tracker, 7, "minecraft:zombie", 0, 0, 0);

		assertEquals(List.of(EntityChangeKind.DISAPPEARED, EntityChangeKind.APPEARED),
				changes.stream().map(EntityChange::kind).toList());
		assertEquals("minecraft:zombie", changes.get(1).entity().type());
	}

	@Test
	void forgetsEveryEntityWhenThePlayerJoinsAgainOrTheWorldIsCleared() {
		EntityTracker tracker = new EntityTracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);
		spawn(tracker, 8, "minecraft:zombie", 0, 0, 0);

		List<EntityChange> joined = tracker.join(PLAYER);
		spawn(tracker, 9, "minecraft:zombie", 0, 0, 0);
		List<EntityChange> cleared = tracker.clear();

		assertEquals(List.of(7, 8), joined.stream().map(change -> change.entity().id()).toList());
		assertTrue(joined.stream().allMatch(change -> change.kind() == EntityChangeKind.DISAPPEARED));
		assertEquals(List.of(9), cleared.stream().map(change -> change.entity().id()).toList());
		assertEquals(List.of(), tracker.clear());
	}

	private static List<EntityChange> spawn(
			EntityTracker tracker,
			int entityId,
			String type,
			double x,
			double y,
			double z
	) {
		return tracker.spawn(entityId, new UUID(0, entityId), type, new Vec3d(x, y, z), 0, 0);
	}

	private static EntityChange single(List<EntityChange> changes) {
		assertEquals(1, changes.size(), changes::toString);
		return changes.getFirst();
	}
}
