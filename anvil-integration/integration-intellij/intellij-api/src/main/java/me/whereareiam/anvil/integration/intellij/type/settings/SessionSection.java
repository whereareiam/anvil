package me.whereareiam.anvil.integration.intellij.type.settings;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Section selected when a new scenario-session tab opens.
 */
@RequiredArgsConstructor
public enum SessionSection {
	LAST_USED("Last used"),
	ENVIRONMENT("Environment"),
	CONSOLE("Console"),
	PLAYERS("Players");

	private final @NotNull String label;

	@Override
	public @NotNull String toString() {
		return label;
	}
}
