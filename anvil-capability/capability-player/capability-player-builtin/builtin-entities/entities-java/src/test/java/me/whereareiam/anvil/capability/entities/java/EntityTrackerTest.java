package me.whereareiam.anvil.capability.entities.java;

import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.EntityItem;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomNameVisible;
import me.whereareiam.anvil.protocol.java.api.model.entity.DisplayText;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
import me.whereareiam.anvil.protocol.java.worker.TestJavaPlayer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTrackerTest {
	private static final int PLAYER = 42;
	private static final Set<Relative> DELTA = EnumSet.of(Relative.X, Relative.Y, Relative.Z);

	private final TestJavaPlayer player = new TestJavaPlayer("Alice");

	@Test
	void reportsASpawnWithTheConnectionAndTheDistanceToThePlacedPlayer() {
		EntityTracker tracker = tracker(3);
		tracker.join(PLAYER);
		place(0, 64, 0);

		EntityChange change = single(spawn(tracker, 7, "minecraft:armor_stand", 3, 68, 0));

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
		EntityTracker tracker = tracker(1);

		assertNull(single(spawn(tracker, 7, "minecraft:zombie", 3, 68, 0)).getEntity().getPlayerDistance());
	}

	@Test
	void appliesDataAndReportsTheEntityBeforeAndAfter() {
		EntityTracker tracker = tracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);

		EntityChange change = single(tracker.change(7, List.of(
				SharedFlags.builder().onFire(true).sneaking(true).invisible(true).glowing(true).build(),
				new CustomName("Alice: hi"),
				new CustomNameVisible(true)
		)));
		EntityChange text = single(tracker.change(7, List.of(new DisplayText("hi"), new CustomName(null),
				new ShownItem(ItemStack.of(871, 3)))));

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
	void showsNoItemForAnEmptyStack() {
		EntityTracker tracker = tracker(1);
		spawn(tracker, 7, "minecraft:item", 0, 0, 0);
		tracker.change(7, List.of(new ShownItem(ItemStack.of(871, 3))));

		assertNull(single(tracker.change(7, List.of(new ShownItem(ItemStack.of(871, 0))))).getEntity().getItem());
		assertEquals(List.of(), tracker.change(7, List.of(new ShownItem(null))));
	}

	@Test
	void reportsNothingForAnUnchangedOrUnknownEntity() {
		EntityTracker tracker = tracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);

		assertEquals(List.of(), tracker.change(7, List.of(SharedFlags.builder().build())));
		assertEquals(List.of(), tracker.change(7, List.of()));
		assertEquals(List.of(), tracker.change(8, List.of(SharedFlags.builder().invisible(true).build())));
		tracker.move(8, new Vec3d(1, 0, 0), Set.of());
		tracker.turn(8, 90, 0, Set.of());
		assertEquals(List.of(7), tracker.known().stream().map(SeenEntity::getId).toList());
		assertEquals(List.of(), tracker.remove(List.of(8)));
	}

	@Test
	void movesByRelativeCoordinatesAndToAbsoluteOnes() {
		EntityTracker tracker = tracker(1);
		spawn(tracker, 7, "minecraft:zombie", 1, 2, 3);

		tracker.move(7, new Vec3d(0.5, -1, 0), DELTA);
		SeenEntity moved = tracker.known().getFirst();
		tracker.move(7, new Vec3d(10, 1, 20), Set.of(Relative.Y));
		SeenEntity teleported = tracker.known().getFirst();

		assertEquals(List.of(1.5, 1.0, 3.0), List.of(moved.getX(), moved.getY(), moved.getZ()));
		assertEquals(List.of(10.0, 2.0, 20.0), List.of(teleported.getX(), teleported.getY(), teleported.getZ()));
	}

	@Test
	void measuresTheDistanceToWhereThePlayerIsWhenRead() {
		EntityTracker tracker = tracker(1);
		place(0, 64, 0);
		spawn(tracker, 7, "minecraft:zombie", 0, 64, 10);

		player.protocol().player().position(new Vec3d(0, 64, 4));
		assertEquals(6.0, tracker.known().getFirst().getPlayerDistance());
		tracker.move(7, new Vec3d(0, 0, -3), DELTA);
		assertEquals(3.0, tracker.known().getFirst().getPlayerDistance());
	}

	@Test
	void carriesTheCurrentPositionInTheNextFact() {
		EntityTracker tracker = tracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);
		tracker.move(7, new Vec3d(5, 0, 0), Set.of());

		EntityChange named = single(tracker.change(7, List.of(new CustomName("hello"))));

		assertEquals(5.0, named.getEntity().getX());
		assertEquals(5.0, named.getPrevious().getX());
	}

	@Test
	void keepsTheSpawnRotationAndTurnsByRelativeAnglesAndToAbsoluteOnes() {
		EntityTracker tracker = tracker(1);
		SeenEntity spawned = single(tracker.spawn(7, null, "minecraft:zombie", new Vec3d(0, 0, 0), 90, 10))
				.getEntity();

		tracker.turn(7, 180, 5, Set.of(Relative.PITCH));
		SeenEntity turned = tracker.known().getFirst();

		assertNull(spawned.getUniqueId());
		assertEquals(List.of(90f, 10f), List.of(spawned.getYaw(), spawned.getPitch()));
		assertEquals(List.of(180f, 15f), List.of(turned.getYaw(), turned.getPitch()));
	}

	@Test
	void tracksWhatRidesThePlayerThroughEveryVehicle() {
		EntityTracker tracker = tracker(1);
		tracker.join(PLAYER);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);
		spawn(tracker, 8, "minecraft:text_display", 0, 0, 0);

		List<EntityChange> onTop = tracker.mount(7, List.of(8));
		List<EntityChange> mounted = tracker.mount(PLAYER, List.of(7));

		assertEquals(List.of(8), onTop.get(0).getEntity().getPassengerIds());
		assertEquals(7, onTop.get(1).getEntity().getVehicleId());
		assertFalse(onTop.get(1).getEntity().isRidingPlayer());
		assertEquals(2, mounted.size());
		assertTrue(mounted.stream().allMatch(change -> change.getEntity().isRidingPlayer()));
		assertEquals(PLAYER, mounted.get(0).getEntity().getVehicleId());

		List<EntityChange> dismounted = tracker.mount(PLAYER, List.of());
		assertEquals(2, dismounted.size());
		assertTrue(dismounted.stream().noneMatch(change -> change.getEntity().isRidingPlayer()));
		assertNull(dismounted.get(0).getEntity().getVehicleId());
	}

	@Test
	void removesEntitiesAndDismountsTheirPassengers() {
		EntityTracker tracker = tracker(1);
		tracker.join(PLAYER);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);
		spawn(tracker, 8, "minecraft:text_display", 0, 0, 0);
		tracker.mount(PLAYER, List.of(7));
		tracker.mount(7, List.of(8));

		List<EntityChange> changes = tracker.remove(List.of(7));

		assertEquals(EntityChangeKind.DISAPPEARED, changes.get(0).getKind());
		assertEquals(7, changes.get(0).getEntity().getId());
		assertEquals(EntityChangeKind.CHANGED, changes.get(1).getKind());
		assertNull(changes.get(1).getEntity().getVehicleId());
		assertFalse(changes.get(1).getEntity().isRidingPlayer());
	}

	@Test
	void replacesAnEntitySpawnedAgainUnderItsId() {
		EntityTracker tracker = tracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);

		List<EntityChange> changes = spawn(tracker, 7, "minecraft:zombie", 0, 0, 0);

		assertEquals(List.of(EntityChangeKind.DISAPPEARED, EntityChangeKind.APPEARED),
				changes.stream().map(EntityChange::getKind).toList());
		assertEquals("minecraft:zombie", changes.get(1).getEntity().getType());
	}

	@Test
	void forgetsEveryEntityWhenThePlayerJoinsAgainOrTheWorldIsCleared() {
		EntityTracker tracker = tracker(1);
		spawn(tracker, 7, "minecraft:armor_stand", 0, 0, 0);
		spawn(tracker, 8, "minecraft:zombie", 0, 0, 0);

		List<EntityChange> joined = tracker.join(PLAYER);
		spawn(tracker, 9, "minecraft:zombie", 0, 0, 0);
		List<EntityChange> cleared = tracker.clear();

		assertEquals(List.of(7, 8), joined.stream().map(change -> change.getEntity().getId()).toList());
		assertTrue(joined.stream().allMatch(change -> change.getKind() == EntityChangeKind.DISAPPEARED));
		assertEquals(List.of(9), cleared.stream().map(change -> change.getEntity().getId()).toList());
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

	private @NotNull EntityTracker tracker(int connection) {
		return new EntityTracker(connection, new PlayerMeasure(player.protocol().player()));
	}

	private void place(double x, double y, double z) {
		player.place(new Vec3d(x, y, z), new ViewRotation(0, 0), true);
	}
}
