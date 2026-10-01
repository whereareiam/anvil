package me.whereareiam.anvil.integration.intellij.settings;

import com.intellij.openapi.Disposable;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.model.settings.CommandScope;
import org.jetbrains.annotations.NotNull;

/**
 * Accepted command history shared by scenario sessions inside one IntelliJ project.
 */
public interface ProjectCommandHistory {
	/**
	 * Returns the native IDE history limit; zero disables retained history.
	 */
	int limit();

	/**
	 * Returns accepted commands for the exact source, scenario, target, and operation scope.
	 */
	@NotNull List<String> history(@NotNull CommandScope scope);

	/**
	 * Records a command after its transport accepts submission.
	 */
	void submitted(@NotNull CommandScope scope, @NotNull String text);

	/**
	 * Delivers history changes on the IDE event thread until the owner is disposed.
	 */
	void subscribe(@NotNull Runnable listener, @NotNull Disposable owner);
}
