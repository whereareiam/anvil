package me.whereareiam.anvil.integration.intellij.scenario.execution;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.Getter;
import me.whereareiam.anvil.integration.intellij.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.log.StoredSessionLog;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.tooling.ToolingLaunch;
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns one environment session's lifecycle state and controls only that session's process execution
 * ID. Closing it releases retained output and requests cleanup while its environment is active.
 */
public final class RetainedEnvironmentSession implements EnvironmentSession {
	private final @NotNull EnvironmentExecution execution;
	private final @NotNull Runnable onDispose;
	private final Map<ActionScope, Map<String, String>> actionDrafts = new HashMap<>();

	/**
	 * Returns the IDE project owning this environment session and its workspace preferences.
	 */
	@Getter
	private final @NotNull Project ideProject;

	/**
	 * Returns the scenario source used to prepare this session.
	 */
	@Getter
	private final @NotNull ScenarioSource source;

	/**
	 * Returns this environment session's stable IDE identity, allocated before its runtime starts.
	 */
	@Getter
	private final @NotNull String id = UUID.randomUUID().toString();

	/**
	 * Returns the retained output owned by this session.
	 */
	private final @NotNull StoredSessionLog log = new StoredSessionLog();
	private final ChangeListeners listeners = new ChangeListeners();

	/**
	 * Returns the retained declaration, enriched when a cold launch loads its scenario definitions.
	 */
	@Getter
	private volatile @NotNull ScenarioDescriptor scenario;

	private volatile boolean disposed;

	RetainedEnvironmentSession(
			@NotNull Project ideProject,
			@NotNull ToolingLaunch launch,
			@NotNull ScenarioDescriptor scenario,
			@Nullable String process,
			@NotNull Runnable onDispose,
			@NotNull Runnable onChange
	) {
		this.ideProject = ideProject;
		this.source = launch.source();
		this.scenario = scenario;
		this.onDispose = onDispose;
		execution = new EnvironmentExecution(launch, scenario, process, log, () -> {
			changed();
			onChange.run();
		}, this::scenario);
	}

	boolean isDisposed() {
		return disposed;
	}

	void start() {
		// Delivery must outlive this handle: a Run console attached to the log keeps reading after the tab closes.
		execution.start(ideProject);
	}

	@Override
	public @NotNull SessionSnapshot getSnapshot() {
		return execution.snapshot();
	}

	@Override
	public @NotNull EnvironmentState getEnvironmentState() {
		return EnvironmentStateCalculator.calculate(scenario, execution.snapshot());
	}

	@Override
	public boolean isActive() {
		return execution.isActive();
	}

	@Override
	public @NotNull CompletableFuture<Boolean> startAll() {
		return disposed ? CompletableFuture.completedFuture(false) : execution.startAll();
	}

	@Override
	public @NotNull CompletableFuture<Boolean> startProcess(@NotNull String process) {
		return disposed ? CompletableFuture.completedFuture(false) : execution.startProcess(process);
	}

	@Override
	public @NotNull CompletableFuture<Boolean> stopProcess(@NotNull String process) {
		return disposed ? CompletableFuture.completedFuture(false) : execution.stopProcess(process);
	}

	@Override
	public @NotNull CompletableFuture<Boolean> restartProcess(@NotNull String process) {
		return disposed ? CompletableFuture.completedFuture(false) : execution.restartProcess(process);
	}

	@Override
	public @NotNull CompletableFuture<Boolean> console(@NotNull String process, @NotNull String text) {
		return disposed ? CompletableFuture.completedFuture(false) : execution.console(process, text);
	}

	/**
	 * Invokes a contributed action and awaits its portable result.
	 *
	 * @param action    selected descriptor; availability is checked again by the runtime
	 * @param arguments entered scalar values
	 * @return completed outcome or a transport/validation failure
	 */
	public @NotNull CompletableFuture<ActionResult> invoke(
			@NotNull ActionDescriptor action,
			@NotNull Map<String, String> arguments
	) {
		return disposed
				? CompletableFuture.failedFuture(new IllegalStateException("Run is closed"))
				: execution.invoke(action, arguments);
	}

	/**
	 * Returns this environment session's unsent values for one action and target.
	 *
	 * @param action action scope
	 * @return retained non-sensitive values
	 */
	@RequiresEdt
	public @NotNull Map<String, String> actionDraft(@NotNull ActionDescriptor action) {
		return actionDrafts.getOrDefault(
				new ActionScope(action.getDefinition().getId(), action.getTarget()), Map.of());
	}

	/**
	 * Retains non-sensitive input drafts only for the lifetime of this environment view.
	 *
	 * @param action action scope and input sensitivity declarations
	 * @param values current form values
	 */
	@RequiresEdt
	public void actionDraft(@NotNull ActionDescriptor action, @NotNull Map<String, String> values) {
		if (disposed) return;

		Map<String, String> safe = new HashMap<>();
		action.getDefinition()
				.getInputs()
				.forEach(
						input -> {
							if (!input.isSensitive() && values.containsKey(input.getName()))
								safe.put(input.getName(), values.get(input.getName()));
						});

		actionDrafts.put(new ActionScope(
				action.getDefinition().getId(),
				action.getTarget()),
				Map.copyOf(safe)
		);
	}

	/**
	 * Requests cleanup of this environment session without affecting a newer session.
	 */
	public void stop() {
		execution.stop();
	}

	@Override
	public @NotNull SessionLog getLog() {
		return log;
	}

	/**
	 * Observes environment changes on the IDE event thread until the subscriber is disposed.
	 */
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable subscriber) {
		listeners.add(listener, subscriber);
	}

	void update(@NotNull SessionSnapshot snapshot) {
		execution.update(snapshot);
	}

	public void scenario(@NotNull ScenarioDescriptor scenario) {
		if (this.scenario.equals(scenario)) return;

		this.scenario = scenario;
		changed();
	}

	public void append(@Nullable String process, @NotNull String text, boolean error) {
		append(process, null, 0, text, error);
	}

	public synchronized void append(
			@Nullable String process,
			@Nullable UUID executionId,
			long sequence,
			@NotNull String text,
			boolean error
	) {
		if (ideProject.isDisposed() || disposed && !isActive()) return;
		log.append(process, executionId, sequence, text, error);
	}

	void finished(int exitCode) {
		execution.finish(exitCode);
	}

	private void changed() {
		if (disposed || ideProject.isDisposed()) return;

		listeners.notifyLater(() -> !disposed && !ideProject.isDisposed());
	}

	@Override
	public synchronized void dispose() {
		disposed = true;
		listeners.clear();
		actionDrafts.clear();
		stop();
		onDispose.run();
		log.releaseHistory();
	}

	private record ActionScope(String id, ActionTarget target) {
	}
}
