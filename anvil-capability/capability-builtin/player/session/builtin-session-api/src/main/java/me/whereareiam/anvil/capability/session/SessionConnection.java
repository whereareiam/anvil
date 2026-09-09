package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.capability.session.model.SessionState;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Player-scoped session actions and observations supplied by an outer connection adapter.
 * Observers are released with the owning player.
 */
public interface SessionConnection {
	/**
	 * Starts native login.
	 */
	void connect();

	/**
	 * Disconnects the current native session.
	 */
	void disconnect();

	/**
	 * Replaces the native session and starts login.
	 */
	void rejoin();

	/**
	 * Registers connection-state observations for this player.
	 * @param observer receives subsequent state changes until the player closes
	 */
	void observe(@NotNull Consumer<SessionState> observer);

	/**
	 * Registers permanent-destruction observation.
	 * @param observer invoked when the native player is permanently destroyed
	 */
	void destroyed(@NotNull Runnable observer);

	/**
	 * Waits for a local observation while preserving native connection diagnostics.
	 * @param condition observation predicate
	 * @param description diagnostic action description
	 * @param timeout positive wait limit
	 */
	void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout);
}
