package me.whereareiam.anvil.capability.vitals;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.vitals.model.Death;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Observes the player's own health, food and deaths as its client knows them, and respawns it after a death.
 *
 * <p>The values are the ones the server last sent the player's current connection. A new connection starts with the
 * values a client starts with, full health and food and a saturation of {@code 5}, until the server sends its own; a
 * player whose connection ended keeps the values it had.</p>
 *
 * <p>A death is the server telling the client that the player died, with the message the death screen shows; every
 * death is kept in order and across reconnects. A player who joins already dead, because it left while dead, is
 * {@link #dead()} without a new death. Like the game's client, the player stays dead until it {@link #respawn()}s,
 * unless the server turned the death screen off with its {@code doImmediateRespawn} rule: the client then respawns by
 * itself as soon as it dies.</p>
 *
 * <pre>{@code
 * Vitals vitals = alice.capability(Vitals.class);
 * DeathWatch deaths = vitals.watchDeaths();
 * server.console().sendCommand("kill Alice");
 * Death death = deaths.died();
 * assertTrue(vitals.dead());
 * vitals.respawn();
 * assertEquals(20F, vitals.health());
 * }</pre>
 */
public interface Vitals extends PlayerCapability {
	/**
	 * Returns the player's health, without waiting.
	 *
	 * @return health in half hearts, {@code 20} for a full bar without bonus health
	 */
	float health();

	/**
	 * Returns the player's food level, without waiting.
	 *
	 * @return food in half drumsticks, from {@code 0} to {@code 20}
	 */
	int food();

	/**
	 * Returns the player's food saturation, which drains before the food level does, without waiting.
	 *
	 * @return saturation
	 */
	float saturation();

	/**
	 * Returns whether the player is dead, as its client shows the death screen: from the moment the server reports its
	 * death, or while its health is zero or less, until the server reports it alive again. A death the client
	 * respawned from by itself does not count.
	 *
	 * @return whether the player is dead
	 */
	boolean dead();

	/**
	 * Returns every death so far, without waiting.
	 *
	 * @return immutable history, oldest first
	 */
	@NotNull List<Death> deaths();

	/**
	 * Starts following the player's deaths from now on. Each watch keeps its own position.
	 *
	 * @return a watch that starts after the last death so far
	 */
	@NotNull DeathWatch watchDeaths();

	/**
	 * Respawns the dead player, using the default timeout, as the death screen's respawn button does.
	 *
	 * @throws IllegalStateException when the player is not dead or not connected, or the server does not respawn it in
	 *                               time
	 */
	default void respawn() {
		respawn(SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Respawns the dead player, as the death screen's respawn button does, and waits until the server reports it
	 * alive again.
	 *
	 * @param timeout maximum wait for the server to respawn the player
	 * @throws IllegalStateException when the player is not dead or not connected, or the server does not respawn it in
	 *                               time
	 */
	void respawn(@NotNull Duration timeout);
}
