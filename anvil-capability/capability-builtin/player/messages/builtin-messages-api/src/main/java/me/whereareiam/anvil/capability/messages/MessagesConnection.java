package me.whereareiam.anvil.capability.messages;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Player-scoped messages actions and observations supplied by an outer connection adapter.
 * Observers are released with the owning player.
 */
public interface MessagesConnection {
	/**
	 * Sends a player chat message.
	 * @param message chat text
	 */
	void chat(@NotNull String message);

	/**
	 * Sends a command without a leading slash.
	 * @param command command text without the leading slash
	 */
	void command(@NotNull String command);

	/**
	 * Registers plain-text message observation.
	 * @param observer receives subsequent messages in delivery order
	 */
	void observe(@NotNull Consumer<String> observer);

	/**
	 * Waits for a local observation while preserving native connection diagnostics.
	 * @param condition observation predicate
	 * @param description diagnostic action description
	 * @param timeout positive wait limit
	 */
	void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout);
}
