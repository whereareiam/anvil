package me.whereareiam.anvil.integration.intellij.environment;

import com.intellij.openapi.Disposable;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Starts and retains scenario environments for one IntelliJ project.
 */
public interface EnvironmentLifecycle {
	/**
	 * Starts the complete selected scenario, reusing its loaded catalog process when possible.
	 */
	@NotNull EnvironmentSession start(
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario
	);

	/**
	 * Starts one process and its dependencies while deferring whole-scenario setup.
	 */
	@NotNull EnvironmentSession startProcess(
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@NotNull String process
	);

	/**
	 * Returns retained environment handles in creation order.
	 */
	@NotNull List<? extends EnvironmentSession> getSessions();

	/**
	 * Returns the active visible environment, if one exists.
	 */
	@Nullable EnvironmentSession getActiveSession();

	/**
	 * Reports whether an environment is preparing, running, or cleaning up.
	 */
	boolean hasActiveSession();

	/**
	 * Delivers environment collection changes on the IDE event thread until disposal.
	 */
	void subscribe(@NotNull Runnable listener, @NotNull Disposable owner);
}
