package me.whereareiam.anvil.capability.players;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.players.model.PlayerVisit;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Follows the players joining and leaving one process from the moment the watch was created, in the order the process
 * reported it. Each wait continues after the join or leave the previous one found, so a player that stays on
 * {@code auth} only briefly on the way to {@code lobby} is seen on {@code auth}, and a player that joins again is
 * matched once per wait. On a proxy, a join without a backend is the player connecting to the proxy itself, and a
 * join of a backend is the proxy connecting the player to it, which a test waits for here rather than by reading
 * {@link Players#backend}, a snapshot that can lag behind the backend's own report.
 *
 * <pre>{@code
 * PlayerWatch auth = context.processes().server("auth").capability(Players.class).watch();
 * session.connect();
 * auth.joined(alice);
 * auth.left(alice);
 * auth.neverJoined(alice, Duration.ofSeconds(3));
 *
 * PlayerWatch proxy = context.processes().proxy("proxy").capability(Players.class).watch();
 * messages.command("server game");
 * proxy.joined(alice, "game");
 * }</pre>
 */
public interface PlayerWatch {
	/**
	 * Waits, using the default timeout, until a player next joins the process.
	 *
	 * @param player the simulated player
	 * @return the visit that began with the join
	 */
	default @NotNull PlayerVisit joined(@NotNull SimulatedPlayer player) {
		return joined(player, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until a player next joins the process. The following wait continues after that join.
	 *
	 * @param player the simulated player
	 * @param timeout maximum wait
	 * @return the visit that began with the join, as it is now
	 * @throws IllegalStateException when the player does not join the process in time
	 */
	@NotNull PlayerVisit joined(@NotNull SimulatedPlayer player, @NotNull Duration timeout);

	/**
	 * Waits, using the default timeout, until a player next leaves the process.
	 *
	 * @param player the simulated player
	 * @return the visit that ended
	 */
	default @NotNull PlayerVisit left(@NotNull SimulatedPlayer player) {
		return left(player, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until a player next leaves the process: on a server by moving to another or by disconnecting, on a proxy
	 * by disconnecting from it. The following wait continues after that leave.
	 *
	 * @param player the simulated player
	 * @param timeout maximum wait
	 * @return the visit that ended
	 * @throws IllegalStateException when the player does not leave the process in time
	 */
	@NotNull PlayerVisit left(@NotNull SimulatedPlayer player, @NotNull Duration timeout);

	/**
	 * Expects that a player does not join the process for a duration, however briefly it would stay. It fails as soon
	 * as the player has joined since the previous wait and returns normally once the duration has passed without such
	 * a join. It does not move the watch.
	 *
	 * @param player the simulated player
	 * @param duration how long the player must not join
	 * @throws IllegalStateException when the player joins the process
	 */
	void neverJoined(@NotNull SimulatedPlayer player, @NotNull Duration duration);

	/**
	 * Expects a player's next join of the process, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #joined(SimulatedPlayer, Duration)} for the same player returns the visit at once.
	 *
	 * @param player the simulated player
	 * @return expectation met once the player has joined the process since the previous wait
	 */
	@NotNull Expectation expectJoined(@NotNull SimulatedPlayer player);

	/**
	 * Waits, using the default timeout, until a proxy next connects a player to a backend.
	 *
	 * @param player the simulated player
	 * @param backend scenario name of the backend
	 * @return the player's stay on the backend, as the proxy reports it
	 */
	default @NotNull PlayerVisit joined(@NotNull SimulatedPlayer player, @NotNull String backend) {
		return joined(player, backend, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until a proxy next connects a player to a backend. The following wait continues after that join. On a
	 * server, which connects players to no backend, it never matches.
	 *
	 * @param player the simulated player
	 * @param backend scenario name of the backend
	 * @param timeout maximum wait
	 * @return the player's stay on the backend, as the proxy reports it now
	 * @throws IllegalStateException when the proxy does not connect the player to the backend in time
	 */
	@NotNull PlayerVisit joined(@NotNull SimulatedPlayer player, @NotNull String backend, @NotNull Duration timeout);

	/**
	 * Waits, using the default timeout, until a proxy next moves a player away from a backend.
	 *
	 * @param player the simulated player
	 * @param backend scenario name of the backend
	 * @return the stay that ended
	 */
	default @NotNull PlayerVisit left(@NotNull SimulatedPlayer player, @NotNull String backend) {
		return left(player, backend, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until a proxy next moves a player away from a backend, by connecting it to another or by the player
	 * disconnecting. The following wait continues after that leave.
	 *
	 * @param player the simulated player
	 * @param backend scenario name of the backend
	 * @param timeout maximum wait
	 * @return the stay that ended
	 * @throws IllegalStateException when the proxy does not move the player away from the backend in time
	 */
	@NotNull PlayerVisit left(@NotNull SimulatedPlayer player, @NotNull String backend, @NotNull Duration timeout);

	/**
	 * Expects that a proxy does not connect a player to a backend for a duration, however briefly the player would
	 * stay. It fails as soon as the proxy has done so since the previous wait and does not move the watch.
	 *
	 * @param player the simulated player
	 * @param backend scenario name of the backend
	 * @param duration how long the proxy must not connect the player to the backend
	 * @throws IllegalStateException when the proxy connects the player to the backend
	 */
	void neverJoined(@NotNull SimulatedPlayer player, @NotNull String backend, @NotNull Duration duration);

	/**
	 * Expects a proxy's next connection of a player to a backend, for {@link SimulatedPlayer#awaitFirst}.
	 *
	 * @param player the simulated player
	 * @param backend scenario name of the backend
	 * @return expectation met once the proxy has connected the player to the backend since the previous wait
	 */
	@NotNull Expectation expectJoined(@NotNull SimulatedPlayer player, @NotNull String backend);
}
