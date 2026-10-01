package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Supervises the lifecycle of one Minecraft server or proxy process generation.
 * Command I/O and output capture are owned by its console.
 */
public abstract class ManagedProcess implements RunningProcess {
	private final String name;
	private final UUID executionId = UUID.randomUUID();
	private final @Nullable CapabilityOwner<ProcessCapability> capabilities;
	private final InetSocketAddress address;
	private final Path workDirectory;
	private final ManagedProcessConsole console;
	private final AtomicReference<ProcessState> state = new AtomicReference<>(ProcessState.CREATED);
	private final CountDownLatch readiness = new CountDownLatch(1);

	private @Nullable ProcessExecution process;
	private volatile boolean failed;
	private volatile boolean cancelled;
	private String stopCommand = "stop";

	/**
	 * Creates a not-yet-started process generation.
	 */
	protected ManagedProcess(
			@NotNull String name,
			@NotNull InetSocketAddress address,
			@NotNull Path workDirectory,
			@Nullable CapabilityOwner<ProcessCapability> capabilities
	) {
		this.name = name;
		this.capabilities = capabilities;
		this.address = address;
		this.workDirectory = workDirectory;
		this.console = new ManagedProcessConsole(name, workDirectory);
	}

	@Override
	public @NotNull UUID executionId() {
		return executionId;
	}

	@Override
	public @NotNull <C extends ProcessCapability> C capability(@NotNull Class<C> type) {
		return capabilities == null ? RunningProcess.super.capability(type) : capabilities.capability(type);
	}

	@Override
	public boolean hasCapability(@NotNull Class<? extends ProcessCapability> type) {
		return capabilities != null && capabilities.hasCapability(type);
	}

	/**
	 * Launches the JVM and waits for its readiness line.
	 * The scenario owner remains responsible for stopping a failed startup attempt.
	 */
	public synchronized void start(
			@NotNull Supplier<ProcessExecution> launch,
			@NotNull Pattern readinessPattern,
			@NotNull String stopCommand,
			@NotNull Duration timeout
	) {
		if (!state.compareAndSet(ProcessState.CREATED, ProcessState.STARTING))
			throw new IllegalStateException("Process '" + name + "' cannot start from state " + state.get());

		this.stopCommand = stopCommand;
		try {
			if (cancelled) throw cancellation();

			ProcessExecution launched = launch.get();
			process = launched;
			console.attach(launched, readinessPattern, this::becameReady, this::outputEnded);
			launched.onExit().thenRun(this::exited);
			awaitReadiness(launched, timeout);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			markFailed();
			throw new ProcessException(name, "Interrupted while starting process '" + name + "'", failure);
		} catch (RuntimeException | Error failure) {
			markFailed();
			throw failure;
		}
	}

	/**
	 * Abandons a start in progress from another thread: the readiness wait returns at once and the start
	 * fails, leaving its launched JVM for the owner's normal stop. Has no effect on a ready generation.
	 */
	public void cancelStart() {
		cancelled = true;
		readiness.countDown();
	}

	/**
	 * Requests graceful shutdown, then escalates to process-tree termination as needed.
	 * A broken command channel does not prevent termination and is reported after the process stops.
	 */
	public synchronized void stop(@NotNull Duration timeout) {
		ProcessExecution current = process;
		if (current == null || !current.isAlive()) {
			ProcessState previous = state.getAndSet(ProcessState.STOPPED);
			if (current != null && previous != ProcessState.STOPPING && previous != ProcessState.STOPPED) failed = true;
			console.closeInput();
			if (current == null) console.closeOutput();
			return;
		}

		ProcessState previous = state.getAndSet(ProcessState.STOPPING);
		if (previous == ProcessState.FAILED) failed = true;
		boolean graceful = previous == ProcessState.READY;
		ProcessException commandFailure = graceful ? requestStop() : null;
		try {
			stopAndAwait(current, timeout, !graceful || commandFailure != null);
			state.set(ProcessState.STOPPED);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			terminateTree(current, true);
			markFailed();
			throw shutdownFailure("Interrupted while stopping process '" + name + "'", failure, commandFailure);
		} catch (ProcessException failure) {
			markFailed();
			if (commandFailure != null) failure.addSuppressed(commandFailure);
			throw failure;
		} finally {
			console.closeInput();
		}

		if (commandFailure != null) throw commandFailure;
	}

	private void awaitReadiness(ProcessExecution launched, Duration timeout) throws InterruptedException {
		if (!readiness.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
			markFailed();
			throw new ProcessException(name, "Process '" + name + "' did not become ready within " + timeout
					+ console.diagnosticTail());
		}
		if (launched.isAlive() && state.get() == ProcessState.READY) return;
		if (cancelled) throw cancellation();

		markFailed();
		throw new ProcessException(name, "Process '" + name + "' exited or ended output before becoming ready"
				+ console.diagnosticTail());
	}

	private ProcessException cancellation() {
		markFailed();

		return new ProcessException(name, "Start of process '" + name + "' was cancelled because the scenario is finishing");
	}

	private void becameReady() {
		if (state.compareAndSet(ProcessState.STARTING, ProcessState.READY)) readiness.countDown();
	}

	private void outputEnded() {
		if (state.compareAndSet(ProcessState.STARTING, ProcessState.FAILED)) { failed = true; readiness.countDown(); }
	}

	private void exited() {
		ProcessState stopped = state.updateAndGet(current -> current == ProcessState.STOPPING || current == ProcessState.STOPPED
				? current : ProcessState.FAILED);
		if (stopped == ProcessState.FAILED) failed = true;
		readiness.countDown();
		console.closeInput();
	}

	/**
	 * Reports failure history even after cleanup changes the visible generation state to stopped.
	 *
	 * @return whether this generation failed before or during shutdown
	 */
	public boolean failed() {
		return failed || state.get() == ProcessState.FAILED;
	}

	private void markFailed() {
		failed = true;
		state.set(ProcessState.FAILED);
	}

	private @Nullable ProcessException requestStop() {
		try {
			console.sendCommand(stopCommand);
			return null;
		} catch (ProcessException failure) {
			return failure;
		}
	}

	private void stopAndAwait(ProcessExecution current, Duration timeout, boolean terminateImmediately) throws InterruptedException {
		if (!terminateImmediately && current.await(timeout)) return;

		long escalationMillis = Math.max(1000, timeout.toMillis() / 3);
		terminateTree(current, false);
		if (current.await(Duration.ofMillis(escalationMillis))) return;

		terminateTree(current, true);
		if (current.await(Duration.ofMillis(escalationMillis))) return;

		throw new ProcessException(name, "Process '" + name + "' did not exit after forced termination");
	}

	private void terminateTree(ProcessExecution current, boolean force) {
		current.terminate(force);
	}

	private ProcessException shutdownFailure(String message, Throwable cause, @Nullable ProcessException commandFailure) {
		ProcessException failure = new ProcessException(name, message, cause);
		if (commandFailure != null) failure.addSuppressed(commandFailure);

		return failure;
	}

	@Override
	public @NotNull String name() {
		return name;
	}

	@Override
	public @NotNull InetSocketAddress address() {
		return address;
	}

	@Override
	public @NotNull Path workDirectory() {
		return workDirectory;
	}

	@Override
	public @NotNull ProcessState state() {
		return state.get();
	}

	@Override
	public @NotNull ProcessConsole console() {
		return console;
	}
}
