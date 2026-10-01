package me.whereareiam.anvil.integration.intellij;

import com.intellij.openapi.Disposable;

import me.whereareiam.anvil.integration.intellij.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.model.CatalogSnapshot;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.jetbrains.annotations.NotNull;

/**
 * Loads and retains the scenario descriptors contributed by one selected scenario source.
 */
public interface ScenarioCatalog {
	/**
	 * Replaces an idle catalog process and loads the selected source's current definitions.
	 */
	void load(@NotNull ScenarioSource source);

	/**
	 * Returns the latest load's source, scenarios, state, and failure as one consistent value.
	 */
	@NotNull CatalogSnapshot snapshot();

	/**
	 * Returns preparation and catalog-process output retained independently of the UI.
	 */
	@NotNull SessionLog getLog();

	/**
	 * Cancels preparation or catalog loading when no environment is active.
	 */
	void cancel();

	/**
	 * Delivers catalog changes on the IDE event thread until the owner is disposed.
	 */
	void subscribe(@NotNull Runnable listener, @NotNull Disposable owner);
}
