package me.whereareiam.anvil.integration.intellij.scenario.environment;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.Getter;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.scenario.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.scenario.tooling.ToolingLaunch;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stable handle for one scenario execution and its output, retained after the environment stops.
 * <p>
 * The session is bound to one tooling launch for its whole life. Disposing it releases its output and requests
 * cleanup while its environment is still active; a newer session is never affected.
 */
public final class EnvironmentSession implements Disposable {
	private final @NotNull EnvironmentExecution execution;
	private final @NotNull Runnable onDispose;
	private final Map<ActionScope, Map<String, String>> actionDrafts = new HashMap<>();
	private final @NotNull SessionLog log = new SessionLog();
	private final ChangeListeners listeners = new ChangeListeners();

	/**
	 * The IntelliJ project that owns this session.
	 */
	@Getter
	private final @NotNull Project ideProject;

	/**
	 * The scenario source this session was prepared from.
	 */
	@Getter
	private final @NotNull ScenarioSource source;

	/**
	 * Stable IDE identity, allocated before preparation starts.
	 */
	@Getter
	private final @NotNull String id = UUID.randomUUID().toString();

	/**
	 * The selected scenario, enriched once a cold launch has loaded the source's scenarios.
	 */
	@Getter
	private volatile @NotNull ScenarioDescriptor scenario;

	private volatile boolean rebuilding;
	private volatile boolean disposed;

	EnvironmentSession(
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

	/**
	 * Returns retained preparation, process, and cleanup output.
	 */
	public @NotNull SessionLog getLog() {
		return log;
	}

	/**
	 * Returns the latest lifecycle and target snapshot, retained after completion.
	 */
	public @NotNull SessionSnapshot getSnapshot() {
		return execution.snapshot();
	}

	/**
	 * Returns the overall status of the environment, its processes, and its setup.
	 */
	public @NotNull Status getStatus() {
		return Status.of(scenario, execution.snapshot());
	}

	/**
	 * Reports whether preparation, execution, or cleanup is still active.
	 */
	public boolean isActive() {
		return execution.isActive();
	}

	/**
	 * Reports whether this session executes the given scenario of the given source.
	 */
	public boolean runs(@NotNull ScenarioSource source, @NotNull ScenarioDescriptor scenario) {
		return this.source.getId().equals(source.getId()) && this.scenario.getDefinition().equals(scenario.getDefinition());
	}

	/**
	 * Reports whether starting the whole scenario has work left: setup, or a process that is not ready.
	 */
	public boolean canStartAll() {
		SessionSnapshot snapshot = execution.snapshot();
		if (!isActive() || snapshot.getState() != SessionState.RUNNING) return false;

		return !snapshot.isSetupComplete() || scenario.getProcesses().stream().anyMatch(definition ->
				snapshot.getProcesses().stream().noneMatch(process ->
						process.getName().equals(definition.getName()) && process.getState() == ProcessState.READY));
	}

	/**
	 * Reports whether a declared process can be started from its current state.
	 */
	public boolean canStartProcess(@NotNull String process) {
		SessionSnapshot snapshot = execution.snapshot();
		if (!isActive() || snapshot.getState() != SessionState.RUNNING) return false;

		ProcessSnapshot live = process(process);
		return live == null || live.getState() == ProcessState.CREATED || live.getState() == ProcessState.STOPPED
				|| live.getState() == ProcessState.FAILED;
	}

	/**
	 * Reports whether this running environment can start the whole scenario's remaining work, or one process.
	 *
	 * @param process declared process name, or null for the whole scenario
	 * @return whether starting is possible, with the reason when it is not
	 */
	public @NotNull Availability availability(@Nullable String process) {
		String name = "“" + scenario.getDisplayName() + "”";
		if (!isActive()) return Availability.unavailable(name + " has ended");

		SessionState state = execution.snapshot().getState();
		if (state == SessionState.STARTING) return Availability.unavailable(name + " is starting");
		if (state != SessionState.RUNNING) return Availability.unavailable(name + " is stopping");
		if (process == null) return canStartAll() ? Availability.AVAILABLE : Availability.unavailable(name + " is already running");

		return canStartProcess(process) ? Availability.AVAILABLE : Availability.unavailable("This process is already running");
	}

	/**
	 * Reports whether a process is ready, so it can be stopped, restarted, or sent commands.
	 */
	public boolean isReady(@NotNull String process) {
		ProcessSnapshot live = process(process);
		return isActive() && execution.snapshot().getState() == SessionState.RUNNING
				&& live != null && live.getState() == ProcessState.READY;
	}

	/**
	 * Reports whether the environment can be stopped: it is preparing, starting, or running.
	 */
	public boolean canStop() {
		SessionState state = execution.snapshot().getState();
		return isActive() && (state == SessionState.STARTING || state == SessionState.RUNNING);
	}

	/**
	 * Returns the latest snapshot of one process, or null before it is created.
	 */
	public @Nullable ProcessSnapshot process(@NotNull String name) {
		return execution.snapshot().getProcesses().stream().filter(process -> process.getName().equals(name)).findFirst().orElse(null);
	}

	/**
	 * Starts the remaining processes and the scenario setup.
	 *
	 * @return whether the transport accepted the request, without waiting for execution
	 */
	public @NotNull CompletableFuture<Boolean> startAll() {
		return disposed ? CompletableFuture.completedFuture(false) : execution.startAll();
	}

	/**
	 * Starts a process and its dependencies.
	 *
	 * @param process declared process name
	 * @return whether the transport accepted the request, without waiting for execution
	 */
	public @NotNull CompletableFuture<Boolean> startProcess(@NotNull String process) {
		return disposed ? CompletableFuture.completedFuture(false) : execution.startProcess(process);
	}

	/**
	 * Stops one process while keeping the environment.
	 *
	 * @param process declared process name
	 * @return whether the transport accepted the request, without waiting for execution
	 */
	public @NotNull CompletableFuture<Boolean> stopProcess(@NotNull String process) {
		return disposed ? CompletableFuture.completedFuture(false) : execution.stopProcess(process);
	}

	/**
	 * Restarts one process with its retained workspace.
	 *
	 * @param process declared process name
	 * @return whether the transport accepted the request, without waiting for execution
	 */
	public @NotNull CompletableFuture<Boolean> restartProcess(@NotNull String process) {
		return disposed ? CompletableFuture.completedFuture(false) : execution.restartProcess(process);
	}

	/**
	 * Rebuilds the project's artifacts, then restarts one process, which installs its assets again from the
	 * rebuilt files. Only one rebuild runs at a time.
	 *
	 * @param process declared process name
	 * @return whether the transport accepted the restart; completes exceptionally when the rebuild failed
	 */
	public @NotNull CompletableFuture<Boolean> rebuildAndRestart(@NotNull String process) {
		if (disposed) return CompletableFuture.completedFuture(false);
		if (rebuilding) return CompletableFuture.failedFuture(new IllegalStateException("The project is already being rebuilt."));

		rebuilding = true;
		changed();
		return execution.rebuild()
				.whenComplete((ignored, failure) -> {
					rebuilding = false;
					changed();
				})
				.thenCompose(ignored -> restartProcess(process));
	}

	/**
	 * Reports whether a {@link #rebuildAndRestart rebuild} is running.
	 */
	public boolean isRebuilding() {
		return rebuilding;
	}

	/**
	 * Submits text to a process console.
	 *
	 * @param process declared process name
	 * @param text console command
	 * @return whether the transport accepted the request; this does not confirm execution in the game
	 */
	public @NotNull CompletableFuture<Boolean> console(@NotNull String process, @NotNull String text) {
		return disposed ? CompletableFuture.completedFuture(false) : execution.console(process, text);
	}

	/**
	 * Invokes a contributed action and awaits its result.
	 *
	 * @param action selected descriptor; the runtime checks its availability again
	 * @param arguments entered values
	 * @return completed outcome, or a transport or validation failure
	 */
	public @NotNull CompletableFuture<ActionResult> invoke(
			@NotNull ActionDescriptor action,
			@NotNull Map<String, String> arguments
	) {
		return disposed
				? CompletableFuture.failedFuture(new IllegalStateException("The environment is closed."))
				: execution.invoke(action, arguments);
	}

	/**
	 * Returns the unsent values retained for one action and target.
	 */
	@RequiresEdt
	public @NotNull Map<String, String> actionDraft(@NotNull ActionDescriptor action) {
		return actionDrafts.getOrDefault(new ActionScope(action.getDefinition().getId(), action.getTarget()), Map.of());
	}

	/**
	 * Retains unsent values of non-sensitive inputs for as long as this session is open.
	 */
	@RequiresEdt
	public void actionDraft(@NotNull ActionDescriptor action, @NotNull Map<String, String> values) {
		if (disposed) return;

		Map<String, String> safe = new HashMap<>();
		action.getDefinition().getInputs().forEach(input -> {
			if (!input.isSensitive() && values.containsKey(input.getName()))
				safe.put(input.getName(), values.get(input.getName()));
		});
		actionDrafts.put(new ActionScope(action.getDefinition().getId(), action.getTarget()), Map.copyOf(safe));
	}

	/**
	 * Requests cleanup of this session without affecting a newer session.
	 */
	public void stop() {
		execution.stop();
	}

	/**
	 * Calls the listener on the IDE event thread after each change, until the subscriber is disposed.
	 */
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable subscriber) {
		listeners.add(listener, subscriber);
	}

	void update(@NotNull SessionSnapshot snapshot) {
		execution.update(snapshot);
	}

	void scenario(@NotNull ScenarioDescriptor scenario) {
		if (this.scenario.equals(scenario)) return;

		this.scenario = scenario;
		changed();
	}

	void finished(int exitCode) {
		execution.finish(exitCode);
	}

	private void changed() {
		if (disposed || ideProject.isDisposed()) return;

		listeners.notifyLater(() -> !disposed && !ideProject.isDisposed());
	}

	/**
	 * Stops this session and releases its retained output.
	 */
	@Override
	public synchronized void dispose() {
		disposed = true;
		listeners.clear();
		actionDrafts.clear();
		stop();
		onDispose.run();
		log.releaseHistory();
	}

	private record ActionScope(String id, ActionTarget target) {}

	/**
	 * Whether a scenario or process can run now.
	 *
	 * @param reason why it cannot run, as a short sentence without a final period; null when it can
	 */
	public record Availability(boolean available, @Nullable String reason) {
		/**
		 * Running is possible.
		 */
		public static final Availability AVAILABLE = new Availability(true, null);

		/**
		 * Running is not possible, for the given reason.
		 */
		public static @NotNull Availability unavailable(@NotNull String reason) {
			return new Availability(false, reason);
		}
	}

	/**
	 * Overall status of an environment, its processes, and its setup. A running session may still have stopped
	 * processes or pending setup.
	 */
	public enum Status {
		/**
		 * No environment has been started.
		 */
		NOT_STARTED,
		/**
		 * The session or one of its processes is starting.
		 */
		STARTING,
		/**
		 * The session is available but no process is ready.
		 */
		READY_TO_START,
		/**
		 * At least one, but not every process is ready.
		 */
		PARTIALLY_RUNNING,
		/**
		 * Every process is ready while scenario setup is still pending.
		 */
		SETUP_PENDING,
		/**
		 * The environment is ready for interaction.
		 */
		RUNNING,
		/**
		 * The environment is releasing its resources.
		 */
		STOPPING,
		/**
		 * Cleanup completed.
		 */
		STOPPED,
		/**
		 * Startup or cleanup failed.
		 */
		FAILED;

		/**
		 * Derives the status from a scenario's declaration and its latest snapshot.
		 */
		public static @NotNull Status of(@NotNull ScenarioDescriptor scenario, @NotNull SessionSnapshot snapshot) {
			switch (snapshot.getState()) {
				case IDLE: return NOT_STARTED;
				case STARTING: return STARTING;
				case STOPPING: return STOPPING;
				case STOPPED: return STOPPED;
				case FAILED: return FAILED;
				case RUNNING: break;
			}

			List<ProcessSnapshot> processes = snapshot.getProcesses();
			if (processes.stream().anyMatch(process -> process.getState() == ProcessState.FAILED)) return FAILED;
			if (processes.stream().anyMatch(process -> process.getState() == ProcessState.STOPPING)) return STOPPING;
			if (processes.stream().anyMatch(process -> process.getState() == ProcessState.STARTING)) return STARTING;

			long ready = processes.stream().filter(process -> process.getState() == ProcessState.READY).count();
			if (ready == 0) return READY_TO_START;
			if (ready < Math.max(scenario.getProcesses().size(), processes.size())) return PARTIALLY_RUNNING;
			if (scenario.isSetupAvailable() && !snapshot.isSetupComplete()) return SETUP_PENDING;

			return RUNNING;
		}
	}
}
