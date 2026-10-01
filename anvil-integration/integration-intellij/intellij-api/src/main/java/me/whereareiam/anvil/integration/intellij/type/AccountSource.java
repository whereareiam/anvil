package me.whereareiam.anvil.integration.intellij.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies which configured account directory owns a credential file.
 */
@Getter
@RequiredArgsConstructor
public enum AccountSource {
	PROJECT("Project"),
	GLOBAL("Global");

	private final @NotNull String label;
}
