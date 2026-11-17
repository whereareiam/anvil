package me.whereareiam.anvil.capability.entities.java;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.protocol.java.api.LocalPlayer;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What of an entity depends on the player of one connection: whether it rides the player, through the entity ID the
 * connection's login named, and its distance to where the client's own player is now, which the server's teleports
 * and the player's own moves set, and which the entity lacks while the client knows no position.
 *
 * <p>Not thread-safe; its {@link EntityTracker} measures under its monitor.</p>
 */
@RequiredArgsConstructor
final class PlayerMeasure {
	private static final int NO_PLAYER = -1;

	private final @NotNull LocalPlayer client;
	private int playerId = NO_PLAYER;

	/**
	 * Takes the entity ID the server let the player into a level with, which passengers of the player name as their
	 * vehicle.
	 *
	 * @param entityId the player's own entity ID
	 */
	void join(int entityId) {
		playerId = entityId;
	}

	/**
	 * Returns an entity with what depends on the player measured now.
	 *
	 * @param entity the entity
	 * @param known every entity the connection knows, through which it may ride the player
	 * @return the measured entity
	 */
	@NotNull SeenEntity measure(@NotNull SeenEntity entity, @NotNull Map<Integer, SeenEntity> known) {
		return entity.toBuilder()
				.ridingPlayer(ridesPlayer(entity, known))
				.playerDistance(client.position().map(at -> distance(entity, at)).orElse(null))
				.build();
	}

	/**
	 * Returns the facts of an entity: everything but its distance to the player, which changes with every move.
	 *
	 * @param entity the entity
	 * @param known every entity the connection knows
	 * @return the entity with its riding of the player
	 */
	@NotNull SeenEntity facts(@NotNull SeenEntity entity, @NotNull Map<Integer, SeenEntity> known) {
		return entity.toBuilder().ridingPlayer(ridesPlayer(entity, known)).build();
	}

	/**
	 * Follows the entity's vehicles down to the one that rides nothing, stopping at an unknown vehicle or a loop.
	 */
	private boolean ridesPlayer(@NotNull SeenEntity entity, @NotNull Map<Integer, SeenEntity> known) {
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

	private static double distance(@NotNull SeenEntity entity, @NotNull Vec3d at) {
		double x = entity.getX() - at.getX();
		double y = entity.getY() - at.getY();
		double z = entity.getZ() - at.getZ();
		return Math.sqrt(x * x + y * y + z * z);
	}
}
