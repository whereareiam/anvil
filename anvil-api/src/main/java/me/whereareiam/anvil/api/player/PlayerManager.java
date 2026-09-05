package me.whereareiam.anvil.api.player;

import me.whereareiam.anvil.api.model.player.PlayerOptions;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Context-owned factory and registry for dynamically created simulated players.
 */
public interface PlayerManager extends AutoCloseable {
	/**
	 * Creates a disconnected offline player using the scenario entrypoint and newest compatible
	 * verified client version.
	 *
	 * @param name unique player name
	 * @return context-owned simulated player
	 */
	@NotNull SimulatedPlayer create(@NotNull String name);

	/**
	 * Creates a disconnected player using explicit overrides where supplied.
	 *
	 * @param options player creation options
	 * @return context-owned simulated player
	 */
	@NotNull SimulatedPlayer create(@NotNull PlayerOptions options);

	/**
	 * Returns an immutable snapshot of currently registered players.
	 *
	 * @return registered players
	 */
	@NotNull Collection<SimulatedPlayer> all();

	/**
	 * Resolves a registered player by name.
	 *
	 * @param name player name
	 * @return matching player
	 */
	@NotNull SimulatedPlayer get(@NotNull String name);

	/**
	 * Permanently destroys and unregisters every player.
	 */
	void destroyAll();

	/**
	 * Releases all players and player-manager resources.
	 *
	 * <p>The default implementation preserves the lightweight global API contract. Runtime
	 * implementations may additionally release protocol, agent, or capability resources.</p>
	 */
	@Override
	default void close() {
		destroyAll();
	}
}
