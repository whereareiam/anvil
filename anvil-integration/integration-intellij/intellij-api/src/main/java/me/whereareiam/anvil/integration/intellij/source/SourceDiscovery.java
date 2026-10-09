package me.whereareiam.anvil.integration.intellij.source;

import com.intellij.openapi.Disposable;
import com.intellij.util.concurrency.annotations.RequiresEdt;

import me.whereareiam.anvil.integration.intellij.model.source.DiscoverySnapshot;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.jetbrains.annotations.NotNull;

/**
 * Owns source selection, sync, and deferred catalog refresh for an open project.
 * Commands run on the IDE event thread. Once initialized, observation continues independently of views.
 */
public interface SourceDiscovery {
	/**
	 * Starts project observation once and discovers the initial source selection.
	 */
	@RequiresEdt
	void initialize();

	/**
	 * Rediscovers imported sources and requests a catalog load when preparation is available.
	 */
	@RequiresEdt
	void refresh();

	/**
	 * Requests native project sync; an existing import is observed rather than repeated.
	 */
	@RequiresEdt
	void sync();

	/**
	 * Selects a discovered source and requests its scenarios when the project is idle.
	 *
	 * @param source source chosen from the current discovery snapshot
	 */
	@RequiresEdt
	void select(@NotNull ScenarioSource source);

	/**
	 * Returns immutable discovery state, including recoverable sync diagnostics.
	 * Safe to read from any thread.
	 *
	 * @return latest source selection and discovery operation state
	 */
	@NotNull DiscoverySnapshot snapshot();

	/**
	 * Observes discovery changes on the IDE event thread until the subscriber is disposed.
	 *
	 * @param listener callback that reads the current snapshot
	 * @param owner subscription lifetime
	 */
	void subscribe(@NotNull Runnable listener, @NotNull Disposable owner);
}
