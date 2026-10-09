package me.whereareiam.anvil.integration.intellij.environment;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import me.whereareiam.anvil.integration.intellij.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Stable retained handle for one scenario execution and its output.
 */
public interface EnvironmentSession extends Disposable {
	/**
	 * Returns the open IntelliJ project that owns this session.
	 */
	@NotNull Project getIdeProject();

	/**
	 * Returns the scenario source used to prepare this session.
	 */
	@NotNull ScenarioSource getSource();

	/**
	 * Returns the stable IDE identity allocated before preparation starts.
	 */
	@NotNull String getId();

	/**
	 * Returns retained preparation, process, and cleanup output.
	 */
	@NotNull SessionLog getLog();

	/**
	 * Returns the selected scenario descriptor, enriched after cold discovery if needed.
	 */
	@NotNull ScenarioDescriptor getScenario();

	/**
	 * Returns the latest lifecycle and target snapshot, retained after completion.
	 */
	@NotNull SessionSnapshot getSnapshot();

	/**
	 * Returns the current aggregate lifecycle of this environment and its processes.
	 */
	@NotNull EnvironmentState getEnvironmentState();

	/**
	 * Reports whether preparation, execution, or cleanup is still active.
	 */
	boolean isActive();

	/**
	 * Starts the remaining processes and scenario setup.
	 * The future reports transport submission, not remote execution completion.
	 *
	 * @return false when the session has closed or the request could not be written
	 */
	@NotNull CompletableFuture<Boolean> startAll();

	/**
	 * Starts a process and its dependencies in this environment.
	 *
	 * @param process declared process name
	 * @return whether the transport accepted the request, without waiting for execution
	 */
	@NotNull CompletableFuture<Boolean> startProcess(@NotNull String process);

	/**
	 * Stops one process while retaining the environment.
	 *
	 * @param process declared process name
	 * @return whether the transport accepted the request, without waiting for execution
	 */
	@NotNull CompletableFuture<Boolean> stopProcess(@NotNull String process);

	/**
	 * Restarts one process with its retained workspace.
	 *
	 * @param process declared process name
	 * @return whether the transport accepted the request, without waiting for execution
	 */
	@NotNull CompletableFuture<Boolean> restartProcess(@NotNull String process);

	/**
	 * Submits text to a process console.
	 *
	 * @param process declared process name
	 * @param text console command text
	 * @return whether the transport accepted the request; this does not confirm in-game execution
	 */
	@NotNull CompletableFuture<Boolean> console(@NotNull String process, @NotNull String text);

	/**
	 * Invokes a contributed action and returns its remote result.
	 */
	@NotNull CompletableFuture<ActionResult> invoke(
			@NotNull ActionDescriptor action,
			@NotNull Map<String, String> arguments
	);

	/**
	 * Returns non-sensitive unsent values retained for one action and target.
	 */
	@NotNull Map<String, String> actionDraft(@NotNull ActionDescriptor action);

	/**
	 * Replaces the non-sensitive unsent values retained for one action and target.
	 */
	void actionDraft(@NotNull ActionDescriptor action, @NotNull Map<String, String> values);

	/**
	 * Requests cleanup of this session without affecting a newer session.
	 */
	void stop();

	/**
	 * Delivers session state changes on the IDE event thread until the owner is disposed.
	 */
	void subscribe(@NotNull Runnable listener, @NotNull Disposable owner);

	/**
	 * Stops this session and releases its retained IDE handle.
	 */
	@Override
	void dispose();
}
