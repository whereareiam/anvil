package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Follows the screens a player sees from the moment it was started: the containers the server opens and the returns to
 * the player's own inventory. A wait finds only a screen that opened after the watch's position, never one that was
 * open already, and moves the position past the screen it found, so the next wait looks for the screen after it. Each
 * watch keeps its own position, so several can follow the same player independently.
 *
 * <pre>{@code
 * InventoryWatch watch = inventory.watch();
 * messages.command("shop");
 * Screen shop = watch.screen("Shop");
 * shop.items().named("Buy").click(InventoryClick.LEFT);
 * Screen confirm = watch.screen("Confirm purchase");
 * confirm.items().named("Yes").click(InventoryClick.LEFT);
 * watch.closed();
 * }</pre>
 */
public interface InventoryWatch {
	/**
	 * Waits, using the default timeout, for a container whose title reads exactly as given.
	 *
	 * @param title the plain text of the title
	 * @return the screen, filled by the server
	 * @throws IllegalStateException when no such screen opens in time
	 */
	default @NotNull Screen screen(@NotNull String title) {
		return screen(title, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for a container whose title reads exactly as given.
	 *
	 * @param title the plain text of the title
	 * @param timeout maximum wait
	 * @return the screen, filled by the server
	 * @throws IllegalStateException when no such screen opens in time
	 */
	@NotNull Screen screen(@NotNull String title, @NotNull Duration timeout);

	/**
	 * Waits, using the default timeout, for a container whose state satisfies a condition.
	 *
	 * @param description what the condition requires, named in failures
	 * @param screen condition on the screen's latest state
	 * @return the screen
	 * @throws IllegalStateException when no such screen opens in time
	 */
	default @NotNull Screen screen(@NotNull String description, @NotNull Predicate<InventorySnapshot> screen) {
		return screen(description, screen, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for a container whose state satisfies a condition, such as holding a certain item.
	 *
	 * @param description what the condition requires, named in failures
	 * @param screen condition on the screen's latest state
	 * @param timeout maximum wait
	 * @return the screen
	 * @throws IllegalStateException when no such screen opens in time
	 */
	@NotNull Screen screen(
			@NotNull String description,
			@NotNull Predicate<InventorySnapshot> screen,
			@NotNull Duration timeout
	);

	/**
	 * Waits, using the default timeout, until the player's own inventory is shown again because the server closed the
	 * container the player had open.
	 *
	 * @throws IllegalStateException when no container closes in time
	 */
	default void closed() {
		closed(SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until the player's own inventory is shown again because the server closed the container the player had
	 * open.
	 *
	 * @param timeout maximum wait
	 * @throws IllegalStateException when no container closes in time
	 */
	void closed(@NotNull Duration timeout);
}
