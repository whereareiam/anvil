package me.whereareiam.anvil.api.player;

import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.model.player.PlayerState;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * A real network client exposed through version-independent capabilities.
 *
 * <p>Capabilities group related operations so the player API can grow without turning this
 * interface into one flat collection of unrelated methods.</p>
 */
public interface SimulatedPlayer {
	/**
	 * Default maximum wait used by observations without an explicit timeout.
	 */
	@NotNull Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

	/**
	 * Returns the configured player name.
	 *
	 * @return player name
	 */
	@NotNull String name();

	/**
	 * Returns the resolved Minecraft client version.
	 *
	 * @return client version
	 */
	@NotNull String clientVersion();

	/**
	 * Resolves a typed behavior available on this player.
	 *
	 * <pre>{@code
	 * Session session = player.capability(Session.class);
	 * session.connect();
	 * }</pre>
	 *
	 * @param type capability interface
	 * @param <C> capability type
	 * @return player-scoped capability implementation
	 * @throws CapabilityUnavailableException when the requested behavior is unavailable
	 */
	@NotNull <C extends PlayerCapability> C capability(@NotNull Class<C> type);

	/**
	 * Reports whether the requested typed behavior is available.
	 *
	 * @param type behavior interface
	 * @return {@code true} when the capability is available
	 */
	boolean hasCapability(@NotNull Class<? extends PlayerCapability> type);

	/**
	 * Returns an immutable snapshot immediately without waiting.
	 *
	 * @return current player state
	 */
	@NotNull PlayerState state();

	/**
	 * Permanently releases and unregisters this player.
	 *
	 * <p>Calling this method repeatedly has no additional effect. Other operations are invalid after
	 * destruction.</p>
	 */
	void destroy();
}
