package me.whereareiam.anvil.capability.worlds;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.worlds.model.WorldChange;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Follows the players moving between a server's worlds, as the server reports it, from the moment the watch was
 * created. Each wait continues after the change the previous one found, so a move made again is matched once per
 * wait and never by a change from before the watch. A world is named by its level's key, with or without its
 * {@code minecraft} namespace.
 *
 * <pre>{@code
 * WorldChangeWatch changes = lobby.capability(Worlds.class).watch();
 * messages.command("warp arena");
 * changes.changed(alice, "minecraft:arena");
 * changes.neverChanged(bob, Duration.ofSeconds(2));
 * }</pre>
 */
public interface WorldChangeWatch {
	/**
	 * Waits, using the default timeout, for the server to move a player to a world.
	 *
	 * @param player the simulated player
	 * @param world the key of the world's level
	 * @return the change into the world
	 */
	default @NotNull WorldChange changed(@NotNull SimulatedPlayer player, @NotNull String world) {
		return changed(player, world, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the server to move a player to a world.
	 *
	 * @param player the simulated player
	 * @param world the key of the world's level
	 * @param timeout maximum wait
	 * @return the change into the world
	 * @throws IllegalStateException when the server does not move the player there in time
	 */
	@NotNull WorldChange changed(@NotNull SimulatedPlayer player, @NotNull String world, @NotNull Duration timeout);

	/**
	 * Waits for the server to move a player to any other world.
	 *
	 * @param player the simulated player
	 * @param timeout maximum wait
	 * @return the change
	 * @throws IllegalStateException when the server does not move the player in time
	 */
	@NotNull WorldChange changed(@NotNull SimulatedPlayer player, @NotNull Duration timeout);

	/**
	 * Expects that the server does not move a player to another world for a duration. It fails as soon as the server
	 * has moved the player since the previous wait, naming the change, and returns normally once the duration has
	 * passed without one. It does not move the watch.
	 *
	 * @param player the simulated player
	 * @param duration how long the player must stay in its world
	 * @throws IllegalStateException when the server moves the player
	 */
	void neverChanged(@NotNull SimulatedPlayer player, @NotNull Duration duration);

	/**
	 * Expects the server to move a player to a world, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #changed(SimulatedPlayer, String, Duration)} for the same player and world returns the change at once.
	 *
	 * @param player the simulated player
	 * @param world the key of the world's level
	 * @return expectation met once the server has moved the player there since the previous wait
	 */
	@NotNull Expectation expectChanged(@NotNull SimulatedPlayer player, @NotNull String world);

	/**
	 * Returns the world changes of every player since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread changes, oldest first
	 */
	@NotNull List<WorldChange> unread();
}
