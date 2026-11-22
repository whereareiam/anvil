package me.whereareiam.anvil.capability.entities.java;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.EntityItem;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.edition.java.protocol.model.entity.CustomName;
import me.whereareiam.anvil.edition.java.protocol.model.entity.CustomNameVisible;
import me.whereareiam.anvil.edition.java.protocol.model.entity.DisplayText;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityDataValue;
import me.whereareiam.anvil.edition.java.protocol.model.entity.SharedFlags;
import me.whereareiam.anvil.edition.java.protocol.model.entity.ShownItem;
import me.whereareiam.anvil.edition.java.protocol.model.inventory.ItemStack;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
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
 * A packet that spawns or removes an entity, or changes what it shows or what it rides, returns the changes it caused,
 * in order, while moves and turns of an entity only update the state that {@link #known()} reads. A spawn over a known
 * ID first removes the old entity, and a change of passengers also changes every entity whose riding of the player it
 * changes. What depends on the player, riding it and the distance to it, its {@link PlayerMeasure} measures whenever a
 * change or {@link #known()} returns an entity.
 *
 * <p>Received packets arrive under the client's lock and reads take that lock first too, so every access holds the
 * monitor after the client's lock.</p>
 */
@RequiredArgsConstructor
final class EntityTracker {
	private final int connection;
	private final @NotNull PlayerMeasure measure;
	private Map<Integer, SeenEntity> entities = new LinkedHashMap<>();

	/**
	 * The server let the player into a level with an entity ID of its own; the client forgets every entity it knew.
	 *
	 * @param entityId the player's own entity ID, which passengers of the player name as their vehicle
	 * @return the disappearance of every known entity
	 */
	synchronized @NotNull List<EntityChange> join(int entityId) {
		List<EntityChange> changes = clear();
		measure.join(entityId);
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
			changes.add(new EntityChange(EntityChangeKind.DISAPPEARED, measure.measure(entity, entities), null));

		entities = new LinkedHashMap<>();
		return changes;
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
		changes.add(new EntityChange(EntityChangeKind.APPEARED, measure.measure(entity, entities), null));
		return changes;
	}

	/**
	 * Moves a known entity. Each coordinate the relatives name is added to the current one, every other is absolute.
	 */
	synchronized void move(int entityId, @NotNull Vec3d position, @NotNull Set<Relative> relatives) {
		entities.computeIfPresent(entityId, (id, entity) -> entity.toBuilder()
				.x(relatives.contains(Relative.X) ? entity.getX() + position.getX() : position.getX())
				.y(relatives.contains(Relative.Y) ? entity.getY() + position.getY() : position.getY())
				.z(relatives.contains(Relative.Z) ? entity.getZ() + position.getZ() : position.getZ())
				.build());
	}

	/**
	 * Turns a known entity. Each angle in degrees the relatives name is added to the current one, every other is set.
	 */
	synchronized void turn(int entityId, float yaw, float pitch, @NotNull Set<Relative> relatives) {
		entities.computeIfPresent(entityId, (id, entity) -> entity.toBuilder()
				.yaw(relatives.contains(Relative.YAW) ? entity.getYaw() + yaw : yaw)
				.pitch(relatives.contains(Relative.PITCH) ? entity.getPitch() + pitch : pitch)
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

			changes.add(new EntityChange(EntityChangeKind.DISAPPEARED, measure.measure(entity, entities), null));
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
	 * Returns every entity the connection knows now, in the order they appeared, measured against the player now.
	 *
	 * @return the known entities
	 */
	synchronized @NotNull List<SeenEntity> known() {
		return entities.values().stream().map(entity -> measure.measure(entity, entities)).toList();
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
			if (before == null || measure.facts(before, previous).equals(measure.facts(entity, next))) continue;

			changes.add(new EntityChange(EntityChangeKind.CHANGED, measure.measure(entity, next), measure.measure(before, previous)));
		}
		return changes;
	}

	/**
	 * An empty stack shows no item, as the client draws none.
	 */
	private static @Nullable EntityItem item(@Nullable ItemStack stack) {
		if (stack == null || stack.getCount() <= 0) return null;

		return new EntityItem(stack.getId(), stack.getCount());
	}
}
