package me.whereareiam.anvil.capability.players;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.players.model.PlayerVisit;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * What one server or proxy knows about the players on it, from the joins and leaves its own platform agent reports.
 * A proxy knows a player from the moment the player logs in to it, also before it connects the player to any backend,
 * and the backend it connected the player to; a server knows the players that joined it.
 *
 * <p>A simulated player is found by every profile its client was granted at its logins, so a player that a proxy or
 * a login plugin renamed, or gave another UUID, is still found, and players that share a username, or whose usernames
 * differ only in letter case, are never mistaken for one another.</p>
 *
 * <pre>{@code
 * Players lobby = context.processes().server("lobby").capability(Players.class);
 * PlayerWatch joins = lobby.watch();
 * session.connect();
 * joins.joined(alice);
 * assertEquals("Alice", lobby.profile(alice).orElseThrow().getUsername());
 * }</pre>
 */
public interface Players extends ProcessCapability {
	/**
	 * Returns every player on this process now, in the order they joined it.
	 *
	 * @return the ongoing visits, oldest first
	 */
	@NotNull List<PlayerVisit> online();

	/**
	 * Returns the profile this process reports for a player while the player is on it. It differs from the profile
	 * the client was granted when the process sees what a proxy forwarded, or what a plugin changed.
	 *
	 * @param player the simulated player
	 * @return the profile, or empty while the player is not on this process
	 */
	@NotNull Optional<PlayerProfile> profile(@NotNull SimulatedPlayer player);

	/**
	 * Returns the backend this proxy connected a player to and has not seen the player leave, as the proxy reports it
	 * now. It is a snapshot that can lag a moment behind the backend's own report of the player joining; to wait for a
	 * move, use {@link PlayerWatch#joined(SimulatedPlayer, String)} on this proxy's watch.
	 *
	 * @param player the simulated player
	 * @return scenario name of the backend, or empty on a server and while the proxy has connected the player to none
	 */
	@NotNull Optional<String> backend(@NotNull SimulatedPlayer player);

	/**
	 * Starts following the players joining and leaving this process from now on. Each watch keeps its own position,
	 * so several can follow the same process independently.
	 *
	 * @return a watch that starts after the last join or leave reported so far
	 */
	@NotNull PlayerWatch watch();
}
