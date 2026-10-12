package me.whereareiam.anvil.integration.intellij.scenario.execution;

import com.intellij.openapi.Disposable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.log.StoredSessionLog;
import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
import me.whereareiam.anvil.integration.intellij.tooling.ToolingLaunch;
import me.whereareiam.anvil.integration.intellij.tooling.protocol.ToolingClient;
import me.whereareiam.anvil.tooling.api.EnvironmentOperations;
import me.whereareiam.anvil.tooling.api.ProcessOperations;
import me.whereareiam.anvil.tooling.api.ScenarioOperations;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.process.ProcessControlRequest;
import me.whereareiam.anvil.tooling.api.model.process.console.ConsoleCommandRequest;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioLaunchRequest;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the state and controls of one environment, permanently bound to one tooling launch.
 */
final class EnvironmentExecution {
	private final @NotNull ToolingLaunch launch;
	private final @NotNull ScenarioDescriptor selection;
	private final @Nullable String process;
	private final @NotNull StoredSessionLog log;
	private final @NotNull Runnable changed;
	private final @NotNull Consumer<ScenarioDescriptor> discovered;

	private volatile @NotNull SessionSnapshot snapshot;
	private volatile boolean finished;
	private volatile @Nullable ToolingClient client;
	private boolean userStopped;

	EnvironmentExecution(
			@NotNull ToolingLaunch launch,
			@NotNull ScenarioDescriptor selection,
			@Nullable String process,
			@NotNull StoredSessionLog log,
			@NotNull Runnable changed,
			@NotNull Consumer<ScenarioDescriptor> discovered
	) {
		this.launch = launch;
		this.selection = selection;
		this.process = process;
		this.log = log;
		this.changed = changed;
		this.discovered = discovered;

		snapshot = SessionSnapshot.builder().state(SessionState.STARTING)
				.definition(selection.getDefinition()).scenario(selection.getName())
				.displayName(selection.getDisplayName()).build();
	}

	/**
	 * Binds to the launch and starts the selected scenario or process once the runner is ready.
	 *
	 * @param owner ends event delivery when disposed; delivery otherwise ends when the launch finishes
	 */
	void start(@NotNull Disposable owner) {
		launch.subscribe(new ToolingLaunch.Listener() {
			@Override
			public void snapshot(@NotNull SessionSnapshot value) {
				update(value);
			}

			@Override
			public void output(@NotNull SessionLogEntry entry) {
				log.append(entry.getProcess(), entry.getExecutionId(), entry.getSequence(), entry.getText(), entry.isError());
			}

			@Override
			public void failed(@NotNull Throwable failure) {
				fail(failure);
			}
		}, owner);

		launch.outcome().thenAccept(this::complete);
		launch.discover().thenAccept(definitions -> definitions.stream()
				.filter(candidate -> candidate.getDefinition().equals(selection.getDefinition())
						&& candidate.getName().equals(selection.getName()))
				.findFirst().ifPresent(discovered));

		launch.ready().whenComplete((connected, failure) -> {
			if (failure != null) {
				failUnlessStopping(failure);
				return;
			}

			client = connected;
			if (launch.isStopping()) return;

			var request = ScenarioLaunchRequest.builder()
					.definition(selection.getDefinition())
					.scenario(selection.getName())
					.target(process)
					.build();
			connected.request(ScenarioOperations.START, request).result().whenComplete((state, startFailure) -> {
				if (startFailure != null) failUnlessStopping(startFailure);
			});
		});
	}

	@NotNull SessionSnapshot snapshot() {
		return snapshot;
	}

	boolean isActive() {
		return !finished;
	}

	synchronized void update(@NotNull SessionSnapshot received) {
		if (finished || received.getState() == SessionState.IDLE) return;
		if (snapshot.getState() == SessionState.FAILED && received.getFailure() == null)
			received = received.toBuilder()
					.state(SessionState.FAILED)
					.failure(snapshot.getFailure())
					.build();

		if (snapshot.equals(received)) return;

		snapshot = received;
		changed.run();

		if (received.getFailure() != null) diagnostic(received.getFailure(), true);
		if (received.getState() == SessionState.STOPPED || received.getState() == SessionState.FAILED) launch.stop();
	}

	@NotNull CompletableFuture<Boolean> startAll() {
		return submit(EnvironmentOperations.START_ALL, null);
	}

	@NotNull CompletableFuture<Boolean> startProcess(@NotNull String name) {
		return submit(ProcessOperations.START, ProcessControlRequest.builder().target(name).build());
	}

	@NotNull CompletableFuture<Boolean> stopProcess(@NotNull String name) {
		return submit(ProcessOperations.STOP, ProcessControlRequest.builder().target(name).build());
	}

	@NotNull CompletableFuture<Boolean> restartProcess(@NotNull String name) {
		return submit(ProcessOperations.RESTART, ProcessControlRequest.builder().target(name).build());
	}

	@NotNull CompletableFuture<Boolean> console(@NotNull String name, @NotNull String text) {
		if (!usable()) return CompletableFuture.completedFuture(false);
		var request = ConsoleCommandRequest.builder().target(name).text(text).build();
		var exchange = client.request(ProcessOperations.CONSOLE, request);
		exchange.result().whenComplete((accepted, failure) -> {
			if (failure != null) {
				commandFailed(failure);
				return;
			}

			if (accepted.getAccepted()) diagnostic("Command accepted by Anvil.", false);
		});

		return exchange.submitted();
	}

	@NotNull CompletableFuture<ActionResult> invoke(
			@NotNull ActionDescriptor action,
			@NotNull Map<String, String> arguments
	) {
		if (!usable()) {
			return CompletableFuture.failedFuture(
					new IllegalStateException("Environment is no longer active")
			);
		}

		String sessionId = snapshot.getSessionId();
		if (sessionId == null)
			return CompletableFuture.failedFuture(new IllegalStateException("Environment is not ready"));

		var request = ActionRequest.builder()
				.sessionId(sessionId)
				.actionId(action.getDefinition().getId())
				.target(action.getTarget())
				.arguments(arguments)
				.build();

		return client.request(EnvironmentOperations.INVOKE, request).result();
	}

	private <Q> CompletableFuture<Boolean> submit(ToolingOperation<Q, SessionSnapshot> operation, @Nullable Q request) {
		if (!usable()) return CompletableFuture.completedFuture(false);
		var exchange = client.request(operation, request);
		exchange.result().whenComplete((state, failure) -> {
			if (failure != null) commandFailed(failure);
		});

		return exchange.submitted();
	}

	private boolean usable() {
		return !finished && !launch.isStopping() && client != null;
	}

	synchronized void stop() {
		if (finished || launch.isStopping()) return;
		userStopped = true;
		if (snapshot.getState() != SessionState.FAILED) {
			snapshot = snapshot.toBuilder().state(SessionState.STOPPING).build();
			changed.run();
		}
		launch.stop();
	}

	/**
	 * Fails the environment unless the failure is the cancellation caused by stopping its launch.
	 */
	private void failUnlessStopping(@NotNull Throwable failure) {
		if (!launch.isStopping()) fail(failure);
	}

	private synchronized void fail(Throwable failure) {
		if (finished || snapshot.getState() == SessionState.FAILED) return;
		String message = failure.getMessage() == null ? failure.toString() : failure.getMessage();
		snapshot = snapshot.toBuilder().state(SessionState.FAILED).failure(message).build();
		diagnostic(message, true);
		changed.run();
		launch.stop();
	}

	private synchronized void complete(ToolingLaunch.Outcome outcome) {
		if (outcome instanceof ToolingLaunch.Outcome.Failed failed) fail(failed.cause());
		if (snapshot.getState() == SessionState.FAILED) {
			finish(1);
			return;
		}

		if (userStopped && outcome.cleanExit()) {
			snapshot = snapshot.toBuilder().state(SessionState.STOPPED).players(List.of()).build();
			finish(outcome.exitCode());
			return;
		}

		if (userStopped) {
			fail(new IllegalStateException("The environment stopped without confirming complete cleanup. Inspect the console."));
			finish(1);
			return;
		}

		if (snapshot.getState() == SessionState.STOPPED) {
			finish(outcome.exitCode());
			return;
		}

		fail(new IllegalStateException("The environment session exited unexpectedly."));
		finish(1);
	}

	synchronized void finish(int exitCode) {
		if (finished) return;

		finished = true;
		log.finish(exitCode);
		changed.run();
	}

	private void commandFailed(Throwable failure) {
		// Commands pending during a stop fail with its cancellation; that is not a command failure.
		if (!launch.isStopping()) diagnostic("Cannot send Anvil action: " + failure.getMessage(), true);
	}

	private void diagnostic(String text, boolean error) {
		log.append(null, null, 0, text + "\n", error);
	}
}
