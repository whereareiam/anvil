package me.whereareiam.anvil.integration.intellij.settings;

import com.intellij.openapi.Disposable;

import java.nio.file.Path;

import me.whereareiam.anvil.integration.intellij.model.settings.PreferenceSnapshot;
import org.jetbrains.annotations.NotNull;

/**
 * Application preferences for Anvil's IDE views, independent of scenario runtime options.
 */
public interface Preferences {
	/**
	 * Returns the complete immutable preference snapshot.
	 */
	@NotNull PreferenceSnapshot snapshot();

	/**
	 * Applies a complete preference snapshot and publishes one change notification.
	 */
	void update(@NotNull PreferenceSnapshot preferences);

	/**
	 * Returns the configured global account directory or the user-local default.
	 */
	@NotNull Path accountsDirectory();

	/**
	 * Delivers preference changes on the IDE event thread until the owner is disposed.
	 */
	void subscribe(@NotNull Runnable listener, @NotNull Disposable owner);
}
