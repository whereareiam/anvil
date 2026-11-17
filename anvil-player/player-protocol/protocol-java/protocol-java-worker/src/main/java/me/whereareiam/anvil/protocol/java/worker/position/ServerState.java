package me.whereareiam.anvil.protocol.java.worker.position;

import lombok.Getter;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import me.whereareiam.anvil.protocol.java.api.type.world.GameEventType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the server set of the client's own entity, as the vanilla client follows it: the entity's ID from the login,
 * the game mode of the login, each respawn and each {@code CHANGE_GAME_MODE} game event, and its death, from a health
 * of zero or less or the player's combat kill until it respawns.
 *
 * <p>Not thread-safe; its owner serializes packets and ticks.</p>
 */
@Getter
final class ServerState {
	/**
	 * The protocol ID of the player's own entity, which the server may choose negative, or {@code null} until the
	 * player joined a level on its current connection.
	 */
	private @Nullable EntityId entityId;
	private @NotNull GameMode gameMode = GameMode.SURVIVAL;
	private boolean dead;

	/**
	 * Follows a packet that sets the player's entity, game mode or death; other packets leave it.
	 *
	 * @param packet the received packet
	 */
	void apply(@NotNull ClientboundPacket packet) {
		switch (packet) {
			case ClientboundLogin login -> {
				entityId = login.getEntityId();
				renew(login.getGameMode());
			}
			case ClientboundRespawn respawn -> renew(respawn.getGameMode());
			case ClientboundGameEvent event -> gameEvent(event);
			case ClientboundSetHealth health -> dead |= health.getHealth() <= 0.0F;
			case ClientboundPlayerCombatKill kill -> dead |= own(kill.getPlayerId());
			default -> {
			}
		}
	}

	/**
	 * Forgets the entity, whose connection ended.
	 */
	void disconnected() {
		entityId = null;
	}

	/**
	 * Tells whether an entity ID is the player's own.
	 *
	 * @param id the entity's ID
	 * @return whether it is the player's entity
	 */
	boolean own(@NotNull EntityId id) {
		return id.equals(entityId);
	}

	/**
	 * Takes a changed game mode; the event's value rounds to the game mode's ID and an unknown ID is survival, as
	 * {@code handleGameEvent} and {@code GameType.byId} read it.
	 */
	private void gameEvent(@NotNull ClientboundGameEvent event) {
		if (event.getEvent() != GameEventType.CHANGE_GAME_MODE) return;

		int id = (int) Math.floor(event.getValue() + 0.5F);
		gameMode = id >= 0 && id < GameMode.values().length ? GameMode.values()[id] : GameMode.SURVIVAL;
	}

	private void renew(@NotNull GameMode mode) {
		dead = false;
		gameMode = mode;
	}
}
