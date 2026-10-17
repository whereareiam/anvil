package me.whereareiam.anvil.capability.entities;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.packet.model.EntitiesRemoved;
import me.whereareiam.anvil.capability.entities.packet.model.EntityDataChanged;
import me.whereareiam.anvil.capability.entities.packet.model.EntityMoved;
import me.whereareiam.anvil.capability.entities.packet.model.EntityPacket;
import me.whereareiam.anvil.capability.entities.packet.model.EntitySpawned;
import me.whereareiam.anvil.capability.entities.packet.model.EntityTurned;
import me.whereareiam.anvil.capability.entities.packet.model.PassengersChanged;
import me.whereareiam.anvil.capability.entities.packet.model.PlayerJoined;
import me.whereareiam.anvil.capability.entities.packet.model.PlayerMoved;
import me.whereareiam.anvil.capability.entities.packet.model.WorldCleared;
import me.whereareiam.anvil.capability.entities.packet.model.data.CustomName;
import me.whereareiam.anvil.capability.entities.packet.model.data.CustomNameVisible;
import me.whereareiam.anvil.capability.entities.packet.model.data.DisplayText;
import me.whereareiam.anvil.capability.entities.packet.model.data.EntityData;
import me.whereareiam.anvil.capability.entities.packet.model.data.SharedFlags;
import me.whereareiam.anvil.capability.entities.packet.model.data.ShownItem;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The entities one connection of a player knows, built from the packets of its native session as a client builds
 * them. It separates the facts about an entity from its continuous state: a packet that spawns or removes an entity,
 * or changes what it shows or what it rides, returns the changes it caused, in order, while moves and turns of an
 * entity or the player only update the state that {@link #known()} reads. A spawn over a known ID first removes the
 * old entity, and a change of passengers also changes every entity whose riding of the player it changes.
 *
 * <p>Entities are kept without what depends on the player; their riding of the player and their distance to it are
 * measured whenever a change or {@link #known()} returns them, against the player's position at that moment.</p>
 *
 * <p>Packets of one session arrive on its network thread and reads on the worker's, so every access holds the
 * monitor.</p>
 */
@RequiredArgsConstructor
final class EntityTracker {
	private static final int NO_PLAYER = -1;

	private final int connection;
	private Map<Integer, SeenEntity> entities = new LinkedHashMap<>();
	private int playerId = NO_PLAYER;
	private double playerX;
	private double playerY;
	private double playerZ;
	private boolean playerPlaced;

	/**
	 * Applies one decoded packet.
	 *
	 * @param packet what the packet says
	 * @return the changes it caused, oldest first; none for a move or a turn
	 */
	synchronized @NotNull List<EntityChange> apply(@NotNull EntityPacket packet) {
		return switch (packet) {
			case PlayerJoined joined -> join(joined.getEntityId());
			case WorldCleared ignored -> clear();
			case PlayerMoved moved -> movePlayer(moved);
			case EntitySpawned spawned -> spawn(spawned);
			case EntityMoved moved -> move(moved);
			case EntityTurned turned -> turn(turned);
			case EntityDataChanged changed -> change(changed);
			case PassengersChanged changed -> mount(changed);
			case EntitiesRemoved removed -> remove(removed.getEntityIds());
		};
	}

	/**
	 * Returns every entity the connection knows now, in the order they appeared, measured against the player's
	 * current position.
	 *
	 * @return the known entities
	 */
	synchronized @NotNull List<SeenEntity> known() {
		return entities.values().stream().map(entity -> measure(entity, entities)).toList();
	}

	private List<EntityChange> join(int entityId) {
		List<EntityChange> changes = clear();
		playerId = entityId;
		return changes;
	}

	private List<EntityChange> clear() {
		List<EntityChange> changes = new ArrayList<>();
		for (SeenEntity entity : entities.values())
			changes.add(new EntityChange(EntityChangeKind.DISAPPEARED, measure(entity, entities), null));

		entities = new LinkedHashMap<>();
		return changes;
	}

	private List<EntityChange> movePlayer(PlayerMoved moved) {
		playerX = moved.isRelativeX() ? playerX + moved.getX() : moved.getX();
		playerY = moved.isRelativeY() ? playerY + moved.getY() : moved.getY();
		playerZ = moved.isRelativeZ() ? playerZ + moved.getZ() : moved.getZ();
		playerPlaced = true;
		return List.of();
	}

	private List<EntityChange> spawn(EntitySpawned spawned) {
		List<EntityChange> changes = new ArrayList<>(remove(List.of(spawned.getEntityId())));
		SeenEntity entity = SeenEntity.builder()
				.connection(connection)
				.id(spawned.getEntityId())
				.uniqueId(spawned.getUniqueId())
				.type(spawned.getType())
				.x(spawned.getX())
				.y(spawned.getY())
				.z(spawned.getZ())
				.yaw(spawned.getYaw())
				.pitch(spawned.getPitch())
				.passengerIds(List.of())
				.build();
		entities.put(entity.getId(), entity);
		changes.add(new EntityChange(EntityChangeKind.APPEARED, measure(entity, entities), null));
		return changes;
	}

	private List<EntityChange> move(EntityMoved moved) {
		entities.computeIfPresent(moved.getEntityId(), (id, entity) -> entity.toBuilder()
				.x(moved.isRelativeX() ? entity.getX() + moved.getX() : moved.getX())
				.y(moved.isRelativeY() ? entity.getY() + moved.getY() : moved.getY())
				.z(moved.isRelativeZ() ? entity.getZ() + moved.getZ() : moved.getZ())
				.build());
		return List.of();
	}

	private List<EntityChange> turn(EntityTurned turned) {
		entities.computeIfPresent(turned.getEntityId(), (id, entity) -> entity.toBuilder()
				.yaw(turned.isRelativeYaw() ? entity.getYaw() + turned.getYaw() : turned.getYaw())
				.pitch(turned.isRelativePitch() ? entity.getPitch() + turned.getPitch() : turned.getPitch())
				.build());
		return List.of();
	}

	private List<EntityChange> change(EntityDataChanged changed) {
		SeenEntity entity = entities.get(changed.getEntityId());
		if (entity == null) return List.of();

		SeenEntity.SeenEntityBuilder builder = entity.toBuilder();
		for (EntityData value : changed.getValues()) {
			switch (value) {
				case SharedFlags flags -> builder
						.onFire(flags.isOnFire())
						.sneaking(flags.isSneaking())
						.sprinting(flags.isSprinting())
						.swimming(flags.isSwimming())
						.invisible(flags.isInvisible())
						.glowing(flags.isGlowing())
						.gliding(flags.isGliding());
				case CustomName name -> builder.customName(name.getName());
				case CustomNameVisible visible -> builder.customNameVisible(visible.isVisible());
				case DisplayText text -> builder.text(text.getText());
				case ShownItem item -> builder.item(item.getItem());
			}
		}
		Map<Integer, SeenEntity> next = new LinkedHashMap<>(entities);
		next.put(entity.getId(), builder.build());
		return replace(next);
	}

	/**
	 * Replaces the passengers of a vehicle, which may be the player or an entity the connection does not know.
	 */
	private List<EntityChange> mount(PassengersChanged changed) {
		int vehicleId = changed.getVehicleId();
		Set<Integer> passengers = new HashSet<>(changed.getPassengerIds());
		Map<Integer, SeenEntity> next = new LinkedHashMap<>(entities);
		next.replaceAll((id, entity) -> {
			boolean rides = passengers.contains(id);
			boolean rode = entity.getVehicleId() != null && entity.getVehicleId() == vehicleId;
			SeenEntity.SeenEntityBuilder builder = entity.toBuilder();
			if (rides) builder.vehicleId(vehicleId);
			else if (rode) builder.vehicleId(null);
			if (id == vehicleId) builder.passengerIds(List.copyOf(changed.getPassengerIds()));
			return builder.build();
		});
		return replace(next);
	}

	/**
	 * Removes entities and dismounts their passengers.
	 */
	private List<EntityChange> remove(List<Integer> entityIds) {
		Set<Integer> removed = new HashSet<>(entityIds);
		List<EntityChange> changes = new ArrayList<>();
		for (SeenEntity entity : entities.values())
			if (removed.contains(entity.getId())) changes.add(new EntityChange(EntityChangeKind.DISAPPEARED, measure(entity, entities), null));
		if (changes.isEmpty()) return changes;

		Map<Integer, SeenEntity> next = new LinkedHashMap<>(entities);
		next.keySet().removeAll(removed);
		next.replaceAll((id, entity) -> {
			SeenEntity.SeenEntityBuilder builder = entity.toBuilder();
			if (entity.getVehicleId() != null && removed.contains(entity.getVehicleId())) builder.vehicleId(null);
			if (entity.getPassengerIds().stream().anyMatch(removed::contains))
				builder.passengerIds(entity.getPassengerIds().stream().filter(passenger -> !removed.contains(passenger)).toList());
			return builder.build();
		});
		changes.addAll(replace(next));
		return changes;
	}

	/**
	 * Takes the next state of the remaining entities and records a change for each whose facts differ: what it shows,
	 * what it rides, what rides it, and whether it rides the player. A difference only in position or rotation, which
	 * the entity's moves already updated, is no change.
	 */
	private List<EntityChange> replace(Map<Integer, SeenEntity> next) {
		Map<Integer, SeenEntity> previous = entities;
		entities = next;
		List<EntityChange> changes = new ArrayList<>();
		for (SeenEntity entity : next.values()) {
			SeenEntity before = previous.get(entity.getId());
			if (before != null && !facts(before, previous).equals(facts(entity, next)))
				changes.add(new EntityChange(EntityChangeKind.CHANGED, measure(entity, next), measure(before, previous)));
		}
		return changes;
	}

	private SeenEntity facts(SeenEntity entity, Map<Integer, SeenEntity> known) {
		return entity.toBuilder().ridingPlayer(ridesPlayer(entity, known)).build();
	}

	private SeenEntity measure(SeenEntity entity, Map<Integer, SeenEntity> known) {
		return entity.toBuilder()
				.ridingPlayer(ridesPlayer(entity, known))
				.playerDistance(playerPlaced ? distance(entity) : null)
				.build();
	}

	/**
	 * Follows the entity's vehicles down to the one that rides nothing, stopping at an unknown vehicle or a loop.
	 */
	private boolean ridesPlayer(SeenEntity entity, Map<Integer, SeenEntity> known) {
		if (playerId == NO_PLAYER) return false;

		Set<Integer> visited = new HashSet<>();
		Integer vehicleId = entity.getVehicleId();
		while (vehicleId != null && visited.add(vehicleId)) {
			if (vehicleId == playerId) return true;

			SeenEntity vehicle = known.get(vehicleId);
			vehicleId = vehicle == null ? null : vehicle.getVehicleId();
		}
		return false;
	}

	private double distance(SeenEntity entity) {
		double x = entity.getX() - playerX;
		double y = entity.getY() - playerY;
		double z = entity.getZ() - playerZ;
		return Math.sqrt(x * x + y * y + z * z);
	}
}
