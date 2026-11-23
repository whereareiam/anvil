package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The screens a player saw, oldest first, each as its latest state: a snapshot of the screen last recorded replaces
 * it, a snapshot of a new screen follows it. A screen keeps its position once listed, so watches can read the history
 * through a {@code HistoryCursor}. The worker's snapshots and the test's reads arrive on different threads, so every
 * access holds the monitor.
 */
final class ScreenHistory {
	private final List<InventorySnapshot> screens = new ArrayList<>();

	/**
	 * Creates the history of a player that has seen only its inventory so far.
	 *
	 * @param initial the state assumed until the worker reports one
	 */
	ScreenHistory(@NotNull InventorySnapshot initial) {
		screens.add(initial);
	}

	/**
	 * Records a screen's state.
	 *
	 * @param snapshot the state the worker reported
	 */
	synchronized void record(@NotNull InventorySnapshot snapshot) {
		if (screens.getLast().screen() == snapshot.screen()) {
			screens.set(screens.size() - 1, snapshot);
			return;
		}

		screens.add(snapshot);
	}

	/**
	 * Returns the state of the screen the player sees.
	 *
	 * @return the latest snapshot
	 */
	synchronized @NotNull InventorySnapshot latest() {
		return screens.getLast();
	}

	/**
	 * Returns the latest state of one screen.
	 *
	 * @param screen the screen's number
	 * @return its latest snapshot, or {@code null} when the player has not seen that screen
	 */
	synchronized @Nullable InventorySnapshot screen(int screen) {
		for (int index = screens.size() - 1; index >= 0; index--)
			if (screens.get(index).screen() == screen) return screens.get(index);

		return null;
	}

	/**
	 * Returns every screen's latest state.
	 *
	 * @return the screens, oldest first
	 */
	synchronized @NotNull List<InventorySnapshot> screens() {
		return List.copyOf(screens);
	}
}
