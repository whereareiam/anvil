package me.whereareiam.anvil.integration.intellij.tooling;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.persistence.AccountWorkspace;
import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.integration.intellij.tooling.process.ToolingCommandReader;
import me.whereareiam.anvil.integration.intellij.tooling.process.ToolingConnection;
import me.whereareiam.anvil.integration.intellij.tooling.protocol.ToolingClient;
import me.whereareiam.anvil.tooling.api.ScenarioOperations;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns one preparation and runner generation, releasing files only after process and output completion.
 * <p>
 * A launch advances through {@link State} and publishes exactly one {@link Outcome} after
 * cleanup. The first failure stops the launch and is reported to listeners immediately; later failures
 * are suppressed by it. A stop request is not a failure: it ends in {@link Outcome.Stopped}.
 */
public final class ToolingLaunch {
	private final @NotNull ScenarioSource source;
	private final @NotNull Inputs inputs;
	private final @NotNull Executor executor;
	private final @NotNull ToolingConnection connection;
	private final @NotNull ToolingClient client;
	private final @NotNull List<Listener> listeners = new CopyOnWriteArrayList<>();
	private final @NotNull List<Disposable> subscriptions = new CopyOnWriteArrayList<>();
	private final @NotNull CompletableFuture<ToolingClient> ready = new CompletableFuture<>();
	private final @NotNull CompletableFuture<List<ScenarioDescriptor>> scenarios = new CompletableFuture<>();
	private final @NotNull CompletableFuture<Outcome> outcome = new CompletableFuture<>();

	private volatile @NotNull State state = State.NEW;
	private volatile @Nullable Throwable failure;
	private volatile boolean runnerStarted;
	private volatile int exitCode;
	private final @NotNull AtomicBoolean discoveryRequested = new AtomicBoolean();
	private final @NotNull AtomicBoolean discoveryStarted = new AtomicBoolean();
	private boolean completing;

	public ToolingLaunch(
			@NotNull ScenarioSource source,
			@NotNull Inputs inputs,
			@NotNull ToolingConnection.Starter starter
	) {
		this(source, inputs, starter, task -> ApplicationManager.getApplication().executeOnPooledThread(task));
	}

	ToolingLaunch(
			@NotNull ScenarioSource source,
			@NotNull Inputs inputs,
			@NotNull ToolingConnection.Starter starter,
			@NotNull Executor executor
	) {
		this.source = source;
		this.inputs = inputs;
		this.executor = executor;
		connection = new ToolingConnection(starter, executor);
		client = new ToolingClient(connection, executor,
				snapshot -> notifyListeners(listener -> listener.snapshot(snapshot)), this::output, this::fail);
		client.ready().whenComplete(this::connected);
	}

	public @NotNull ScenarioSource source() {
		return source;
	}

	/**
	 * Returns the current lifecycle state.
	 */
	public @NotNull State state() {
		return state;
	}

	/**
	 * Completes with the connected client, or exceptionally when the launch stops or fails first.
	 */
	public @NotNull CompletableFuture<ToolingClient> ready() {
		return ready.copy();
	}

	/**
	 * Requests the prepared runtime's scenario descriptors once and returns the shared result.
	 * <p>
	 * Discovery runs only when requested, as the first request after the handshake. A discovery failure
	 * fails the launch; stopping the launch completes the result exceptionally.
	 */
	public @NotNull CompletableFuture<List<ScenarioDescriptor>> discover() {
		discoveryRequested.set(true);
		if (ready.isDone() && !ready.isCompletedExceptionally()) requestDiscovery();

		return scenarios.copy();
	}

	/**
	 * Completes once, after every process ended and every owned file was released.
	 */
	public @NotNull CompletableFuture<Outcome> outcome() {
		return outcome.copy();
	}

	public boolean isFinished() {
		return state == State.FINISHED;
	}

	/**
	 * Reports whether a stop was requested or a failure began shutdown.
	 */
	public boolean isStopping() {
		return connection.isStopping();
	}

	/**
	 * Delivers events until the launch finishes or the owner is disposed, whichever comes first. The
	 * registration is released at that point, so a long-lived owner does not retain finished launches.
	 * A throwing listener fails the launch; delivery to the other listeners and resource cleanup continue.
	 */
	public void subscribe(@NotNull Listener listener, @NotNull Disposable owner) {
		Disposable subscription = Disposer.newDisposable("Anvil tooling launch listener");
		if (!Disposer.tryRegister(owner, subscription)) return;

		Disposer.register(subscription, () -> {
			listeners.remove(listener);
			subscriptions.remove(subscription);
		});
		listeners.add(listener);
		subscriptions.add(subscription);
		// A launch that finished while subscribing releases the registration here instead of in complete().
		if (isFinished()) Disposer.dispose(subscription);
	}

	/**
	 * Starts preparation after the previous launch has released its processes and files.
	 */
	void startAfter(@Nullable ToolingLaunch previous) {
		synchronized (this) {
			if (state != State.NEW) throw new IllegalStateException("Tooling launch is already scheduled.");
			state = State.WAITING;
		}

		CompletableFuture<?> predecessor = previous == null ? CompletableFuture.completedFuture(null) : previous.outcome;
		try {
			predecessor.thenRunAsync(this::execute, executor).whenComplete(this::complete);
		} catch (RuntimeException schedulingFailure) {
			complete(null, schedulingFailure);
		}
	}

	/**
	 * Requests shutdown. Pending requests fail with the recorded failure, or with a cancellation.
	 */
	public void stop() {
		advance(State.STOPPING);
		try {
			connection.stop();
		} catch (RuntimeException | Error stoppingFailure) {
			report(stoppingFailure);
		} finally {
			closeClient(failure == null ? new CancellationException("Tooling launch stopped") : failure);
		}
	}

	private void connected(@Nullable Void ignored, @Nullable Throwable error) {
		if (error != null) {
			scenarios.completeExceptionally(error);
			ready.completeExceptionally(error);
			return;
		}

		// Discovery requested before the handshake is sent first; a request racing the handshake is sent here.
		if (discoveryRequested.get()) requestDiscovery();
		advance(State.READY);
		ready.complete(client);
		if (discoveryRequested.get()) requestDiscovery();
	}

	private void requestDiscovery() {
		if (!discoveryStarted.compareAndSet(false, true)) return;
		client.request(ScenarioOperations.DISCOVER, null).result().whenComplete((definitions, error) -> {
			if (error == null) {
				scenarios.complete(definitions);
				return;
			}

			scenarios.completeExceptionally(error);
			if (!isStopping()) fail(error);
		});
	}

	private void execute() {
		try (var resources = new LaunchResources(connection)) {
			prepareAndRun(resources);
		} catch (Exception | Error cleanupFailure) {
			fail(cleanupFailure);
		}
	}

	private void prepareAndRun(@NotNull LaunchResources resources) {
		// Record execution failure before automatic cleanup adds its own failures.
		try {
			if (isStopping()) return;
			Path manifest = prepare(resources);
			if (isStopping()) return;
			run(resources, manifest);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			if (!isStopping()) fail(interrupted);
		} catch (Exception | Error executionFailure) {
			if (!isStopping()) fail(executionFailure);
		}
	}

	private @NotNull Path prepare(@NotNull LaunchResources resources) throws IOException, InterruptedException {
		advance(State.PREPARING);
		var preparation = inputs.prepare(source);
		resources.manifest = new Manifest(preparation.getManifestPath());
		ProcessBuilder command = new ProcessBuilder(preparation.getCommand())
				.directory(preparation.getWorkingDirectory().toFile())
				.redirectErrorStream(true);
		command.environment().putAll(preparation.getEnvironment());
		Process build = connection.start(command);
		int result = connection.readPreparation(build, line -> diagnostic(line, false));
		if (result != 0 && !isStopping())
			throw new IOException("Could not prepare scenarios. Open the console for build details.");

		return preparation.getManifestPath();
	}

	private void run(@NotNull LaunchResources resources, @NotNull Path manifest) throws IOException, InterruptedException {
		resources.accounts = inputs.accounts();
		List<String> command = ToolingCommandReader.command(manifest, resources.accounts.getDirectory());
		Process runner = connection.startRunner(new ProcessBuilder(command)
				.directory(source.getDirectory().toFile()));
		runnerStarted = true;
		advance(State.STARTING);
		exitCode = connection.readRunner(runner, client::receive, line -> diagnostic(line, true), this::fail);
		if (isStopping()) return;
		if (!ready.isDone()) throw new IOException("Tooling runner exited before becoming ready.");
		if (exitCode != 0) throw new IOException("Tooling runner exited with code " + exitCode + ".");
	}

	private void complete(@Nullable Void ignored, @Nullable Throwable schedulingFailure) {
		synchronized (this) {
			if (completing) return;
			completing = true;
		}

		if (schedulingFailure != null) fail(unwrap(schedulingFailure));

		Throwable recorded = failure;
		closeClient(recorded == null ? new CancellationException("Tooling runner closed") : recorded);

		advance(State.FINISHED);
		// Release listeners before publishing, so code awaiting the outcome observes a cleaned-up launch.
		subscriptions.forEach(Disposer::dispose);
		listeners.clear();
		outcome.complete(result(recorded));
	}

	private @NotNull Outcome result(@Nullable Throwable recorded) {
		if (recorded != null) return new Outcome.Failed(recorded);
		if (isStopping()) return new Outcome.Stopped(runnerStarted, exitCode);

		return new Outcome.Finished(exitCode);
	}

	private synchronized void advance(@NotNull State next) {
		if (next.ordinal() > state.ordinal()) state = next;
	}

	/**
	 * Records the first failure, stops the launch, then tells listeners.
	 */
	private void fail(@NotNull Throwable cause) {
		if (!recordFailure(cause)) return;

		stop();
		notifyListeners(listener -> listener.failed(cause));
	}

	/**
	 * Records and reports a failure raised while already stopping, without requesting another stop.
	 */
	private void report(@NotNull Throwable cause) {
		if (recordFailure(cause)) notifyListeners(listener -> listener.failed(cause));
	}

	private synchronized boolean recordFailure(@NotNull Throwable cause) {
		if (failure != null) {
			if (failure != cause) failure.addSuppressed(cause);
			return false;
		}

		failure = cause;

		return true;
	}

	private void closeClient(@NotNull Throwable cause) {
		client.close(cause);
		ready.completeExceptionally(cause);
		scenarios.completeExceptionally(cause);
	}

	private void diagnostic(@NotNull String line, boolean error) {
		output(new SessionLogEntry(null, null, 0, line + "\n", error));
	}

	private void output(@NotNull SessionLogEntry entry) {
		notifyListeners(listener -> listener.output(entry));
	}

	private void notifyListeners(@NotNull Consumer<Listener> delivery) {
		for (Listener listener : listeners) {
			try {
				delivery.accept(listener);
			} catch (RuntimeException | Error listenerFailure) {
				listeners.remove(listener);
				fail(listenerFailure);
			}
		}
	}

	private static @NotNull Throwable unwrap(@NotNull Throwable failure) {
		return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
	}

	@RequiredArgsConstructor
	private static final class LaunchResources implements AutoCloseable {
		private final @NotNull ToolingConnection connection;
		private @Nullable Manifest manifest;
		private @Nullable AccountWorkspace accounts;

		@Override
		public void close() throws IOException {
			// The process and readers finish first; resource closure then releases accounts and the manifest.
			try (Manifest ownedManifest = manifest; AccountWorkspace ownedAccounts = accounts) {
				connection.close();
			}
		}
	}

	private record Manifest(@NotNull Path path) implements AutoCloseable {
		@Override
		public void close() throws IOException {
			Files.deleteIfExists(path);
		}
	}

	/**
	 * Receives runner snapshots, output, and the first failure. Callbacks may run on any thread.
	 */
	public interface Listener {
		default void snapshot(@NotNull SessionSnapshot snapshot) {}

		default void output(@NotNull SessionLogEntry entry) {}

		/**
		 * Reports the first failure as soon as it occurs, before cleanup completes {@link ToolingLaunch#outcome()}.
		 */
		default void failed(@NotNull Throwable failure) {}
	}

	/**
	 * Lifecycle of one tooling launch. States only advance; {@link #STOPPING} may follow any earlier state.
	 */
	public enum State {
		/**
		 * Created but not yet scheduled.
		 */
		NEW,
		/**
		 * Scheduled and waiting for the previous launch's cleanup.
		 */
		WAITING,
		/**
		 * Running the build integration's preparation command.
		 */
		PREPARING,
		/**
		 * Runner process started; waiting for the protocol handshake.
		 */
		STARTING,
		/**
		 * Runner connected and accepting requests.
		 */
		READY,
		/**
		 * A stop was requested or a failure occurred; processes and files are being released.
		 */
		STOPPING,
		/**
		 * Processes ended and every owned file was released; {@link ToolingLaunch#outcome()} is complete.
		 */
		FINISHED
	}

	/**
	 * Terminal result of a tooling launch, published once after every owned resource is released.
	 */
	public sealed interface Outcome {
		/**
		 * Reports whether the runner ended cleanly: it never started, or it exited with code zero.
		 */
		boolean cleanExit();

		/**
		 * Returns the runner exit code, or {@code 1} when the launch failed.
		 */
		int exitCode();

		/**
		 * The runner exited by itself without a stop request.
		 */
		record Finished(int exitCode) implements Outcome {
			@Override
			public boolean cleanExit() {
				return exitCode == 0;
			}
		}

		/**
		 * A stop was requested before the launch finished.
		 *
		 * @param runnerStarted whether the runner process had started
		 * @param exitCode runner exit code, or zero when it never started
		 */
		record Stopped(boolean runnerStarted, int exitCode) implements Outcome {
			@Override
			public boolean cleanExit() {
				return !runnerStarted || exitCode == 0;
			}
		}

		/**
		 * Preparation, the runner, cleanup, or a listener failed. Later failures are suppressed by the first.
		 */
		record Failed(@NotNull Throwable cause) implements Outcome {
			@Override
			public boolean cleanExit() {
				return false;
			}

			@Override
			public int exitCode() {
				return 1;
			}
		}
	}

	/**
	 * Project services a tooling launch reads: the source's preparation and the account workspace.
	 * A launch receives its inputs explicitly instead of looking up project services while it runs.
	 */
	@RequiredArgsConstructor
	public static final class Inputs {
		private final @NotNull BuildIntegrations integrations;
		private final @NotNull AccountLibrary accounts;
		private final @NotNull Preferences preferences;

		/**
		 * Binds the inputs to the project's registered services.
		 */
		public static @NotNull Inputs of(@NotNull Project project) {
			return new Inputs(
					project.getService(BuildIntegrations.class),
					project.getService(AccountLibrary.class),
					ApplicationManager.getApplication().getService(Preferences.class));
		}

		/**
		 * Resolves the preparation command and launch manifest for a source.
		 */
		@NotNull ScenarioPreparation prepare(@NotNull ScenarioSource source) throws IOException {
			return integrations.prepare(source);
		}

		/**
		 * Materializes the configured accounts into a temporary workspace owned by the caller.
		 */
		@NotNull AccountWorkspace accounts() throws IOException {
			return AccountWorkspace.create(accounts, preferences);
		}
	}
}
