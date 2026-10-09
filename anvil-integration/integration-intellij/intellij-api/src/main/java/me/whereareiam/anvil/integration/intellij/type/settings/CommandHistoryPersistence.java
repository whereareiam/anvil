package me.whereareiam.anvil.integration.intellij.type.settings;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Persistence lifetime for project command history.
 */
@RequiredArgsConstructor
public enum CommandHistoryPersistence {
	SESSION("Current IDE session"),
	PROJECT("Across IDE restarts");

	private final @NotNull String label;

	@Override
	public @NotNull String toString() {
		return label;
	}
}
