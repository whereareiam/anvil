package me.whereareiam.anvil.capability.entities.java;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.EntityItem;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomNameVisible;
import me.whereareiam.anvil.protocol.java.api.model.entity.DisplayText;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityDataValue;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The entities one connection of a player knows, built from the packets of the connection as a client builds them.
 * It separates the facts about an entity from its continuous state: a packet that spawns or removes an entity, or
 * changes what it shows or what it rides, returns the changes it caused, in order, while moves and turns of an entity
 * or the player only update the state that {@link #known()} reads. A spawn over a known ID first removes the old
 * entity, and a change of passengers also changes every entity whose riding of the player it changes.
 *
 * <p>Entities are kept without what depends on the player; their riding of the player and their distance to it are
 * measured whenever a change or {@link #known()} returns them, against the player's position at that moment.</p>
 *
 * <p>Received packets arrive on the connection's thread, the player's own moves on the thread that sends them and
 * reads on the worker's, so every access holds the monitor.</p>
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
	 * The server let the player into a level with an entity ID of its own; the client forgets every entity it knew.
	 *
	 * @param entityId the player's own entity ID, which passengers of the player name as their vehicle
	 * @return the disappearance of every known entity
	 */
	synchronized @NotNull List<EntityChange> join(int entityId) {
		List<EntityChange> changes = clear();
		playerId = entityId;
		return changes;
	}

	/**
	 * The client forgets every entity it knew: the player respawned or changed world, the server started configuring
	 * the connection again, or the connection ended.
	 *
	 * @return the disappearance of every known entity
	 */
	synchronized @NotNull List<EntityChange> clear() {
		List<EntityChange> changes = new ArrayList<>();
		for (SeenEntity entity : entities.values())
			changes.add(new EntityChange(EntityChangeKind.DISAPPEARED, measure(entity, entities), null));

		entities = new LinkedHashMap<>();
		return changes;
	}

	/**
	 * Moves the player: the server placed it, or the player sent its own movement. Each coordinate is either absolute
	 * or added to the current one.
	 */
	synchronized void movePlayer(
			double x,
			double y,
			double z,
			boolean relativeX,
			boolean relativeY,
			boolean relativeZ
	) {
		playerX = relativeX ? playerX + x : x;
		playerY = relativeY ? playerY + y : y;
		playerZ = relativeZ ? playerZ + z : z;
		playerPlaced = true;
	}

	/**
	 * The server spawned an entity.
	 *
	 * @param entityId the entity's ID
	 * @param uniqueId the entity's unique ID, or {@code null} for a packet that has none
	 * @param type the entity's type with its namespace
	 * @param position where the entity appears
	 * @param yaw the horizontal direction in degrees
	 * @param pitch the vertical direction in degrees
	 * @return the disappearance of an entity known under the same ID, then the appearance
	 */
	synchronized @NotNull List<EntityChange> spawn(
			int entityId,
			@Nullable UUID uniqueId,
			@NotNull String type,
			@NotNull Vec3d position,
			float yaw,
			float pitch
	) {
		List<EntityChange> changes = new ArrayList<>(remove(List.of(entityId)));
		SeenEntity entity = SeenEntity.builder()
				.connection(connection)
				.id(entityId)
				.uniqueId(uniqueId)
				.type(type)
				.x(position.getX())
				.y(position.getY())
				.z(position.getZ())
				.yaw(yaw)
				.pitch(pitch)
				.passengerIds(List.of())
				.build();
		entities.put(entityId, entity);
		changes.add(new EntityChange(EntityChangeKind.APPEARED, measure(entity, entities), null));
		return changes;
	}

	/**
	 * Moves a known entity. Each coordinate is either absolute or added to the current one.
	 */
	synchronized void move(
			int entityId,
			double x,
			double y,
			double z,
			boolean relativeX,
			boolean relativeY,
			boolean relativeZ
	) {
		entities.computeIfPresent(entityId, (id, entity) -> entity.toBuilder()
				.x(relativeX ? entity.getX() + x : x)
				.y(relativeY ? entity.getY() + y : y)
				.z(relativeZ ? entity.getZ() + z : z)
				.build());
	}

	/**
	 * Turns a known entity. Each angle, in degrees, is either absolute or added to the current one.
	 */
	synchronized void turn(int entityId, float yaw, float pitch, boolean relativeYaw, boolean relativePitch) {
		entities.computeIfPresent(entityId, (id, entity) -> entity.toBuilder()
				.yaw(relativeYaw ? entity.getYaw() + yaw : yaw)
				.pitch(relativePitch ? entity.getPitch() + pitch : pitch)
				.build());
	}

	/**
	 * The server changed data of an entity.
	 *
	 * @param entityId the entity's ID
	 * @param values the changed values the protocol models
	 * @return the change, or none for an unknown entity or values that change no fact
	 */
	synchronized @NotNull List<EntityChange> change(int entityId, @NotNull List<EntityDataValue> values) {
		SeenEntity entity = entities.get(entityId);
		if (entity == null) return List.of();

		SeenEntity.SeenEntityBuilder builder = entity.toBuilder();
		for (EntityDataValue value : values) {
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
				case ShownItem item -> builder.item(item(item.getItem()));
			}
		}
		Map<Integer, SeenEntity> next = new LinkedHashMap<>(entities);
		next.put(entityId, builder.build());
		return replace(next);
	}

	/**
	 * Replaces the passengers of a vehicle, which may be the player or an entity the connection does not know.
	 *
	 * @param vehicleId the vehicle's ID
	 * @param passengerIds the IDs of every entity riding it now
	 * @return the change of every entity that mounted, dismounted or carries other passengers now
	 */
	synchronized @NotNull List<EntityChange> mount(int vehicleId, @NotNull List<Integer> passengerIds) {
		Set<Integer> passengers = new HashSet<>(passengerIds);
		Map<Integer, SeenEntity> next = new LinkedHashMap<>(entities);
		next.replaceAll((id, entity) -> {
			boolean rides = passengers.contains(id);
			boolean rode = entity.getVehicleId() != null && entity.getVehicleId() == vehicleId;
			SeenEntity.SeenEntityBuilder builder = entity.toBuilder();
			if (rides) builder.vehicleId(vehicleId);
			else if (rode) builder.vehicleId(null);
			if (id == vehicleId) builder.passengerIds(List.copyOf(passengerIds));
			return builder.build();
		});
		return replace(next);
	}

	/**
	 * Removes entities and dismounts their passengers.
	 *
	 * @param entityIds the IDs of the removed entities
	 * @return the disappearance of every known removed entity, then the change of every passenger or vehicle of one
	 */
	synchronized @NotNull List<EntityChange> remove(@NotNull List<Integer> entityIds) {
		Set<Integer> removed = new HashSet<>(entityIds);
		List<EntityChange> changes = new ArrayList<>();
		for (SeenEntity entity : entities.values()) {
			if (!removed.contains(entity.getId())) continue;

			changes.add(new EntityChange(EntityChangeKind.DISAPPEARED, measure(entity, entities), null));
		}
		if (changes.isEmpty()) return changes;

		Map<Integer, SeenEntity> next = new LinkedHashMap<>(entities);
		next.keySet().removeAll(removed);
		next.replaceAll((id, entity) -> {
			SeenEntity.SeenEntityBuilder builder = entity.toBuilder();
			if (entity.getVehicleId() != null && removed.contains(entity.getVehicleId())) builder.vehicleId(null);
			List<Integer> passengers = entity.getPassengerIds();
			if (passengers.stream().anyMatch(removed::contains))
				builder.passengerIds(passengers.stream().filter(passenger -> !removed.contains(passenger)).toList());
			return builder.build();
		});
		changes.addAll(replace(next));
		return changes;
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
			if (before == null || facts(before, previous).equals(facts(entity, next))) continue;

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

	/**
	 * An empty stack shows no item, as the client draws none.
	 */
	private static @Nullable EntityItem item(@Nullable ItemStack stack) {
		if (stack == null || stack.getCount() <= 0) return null;

		return new EntityItem(stack.getId(), stack.getCount());
	}
}
