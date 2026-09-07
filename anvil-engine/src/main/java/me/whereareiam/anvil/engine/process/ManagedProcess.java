package me.whereareiam.anvil.engine.process;

import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.ProcessState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.whereareiam.anvil.execution.api.process.ProcessExecution;
import java.util.function.Supplier;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Supervises the lifecycle of one Minecraft server or proxy process generation.
 * Command I/O and output capture are owned by its console.
 */
public abstract class ManagedProcess implements RunningProcess {
	private final String name;
	private final InetSocketAddress address;
	private final Path workDirectory;
	private final ManagedProcessConsole console;
	private final AtomicReference<ProcessState> state = new AtomicReference<>(ProcessState.CREATED);
	private final CountDownLatch readiness = new CountDownLatch(1);

	private @Nullable ProcessExecution process;
	private String stopCommand = "stop";

	/**
	 * Creates a not-yet-started process generation.
	 */
	protected ManagedProcess(@NotNull String name, @NotNull InetSocketAddress address, @NotNull Path workDirectory) {
		this.name = name;
		this.address = address;
		this.workDirectory = workDirectory;
		this.console = new ManagedProcessConsole(name, workDirectory);
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
			ProcessExecution launched = launch.get();
			process = launched;
			console.attach(launched, readinessPattern, this::becameReady, this::outputEnded);
			launched.onExit().thenRun(this::exited);
			awaitReadiness(launched, timeout);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			state.set(ProcessState.FAILED);
			throw new ProcessException(name, "Interrupted while starting process '" + name + "'", failure);
		} catch (RuntimeException | Error failure) {
			state.set(ProcessState.FAILED);
			throw failure;
		}
	}

	/**
	 * Requests graceful shutdown, then escalates to process-tree termination as needed.
	 * A broken command channel does not prevent termination and is reported after the process stops.
	 */
	public synchronized void stop(@NotNull Duration timeout) {
		ProcessExecution current = process;
		if (current == null || !current.isAlive()) {
			state.set(ProcessState.STOPPED);
			console.closeInput();
			return;
		}

		ProcessState previous = state.getAndSet(ProcessState.STOPPING);
		boolean graceful = previous == ProcessState.READY;
		ProcessException commandFailure = graceful ? requestStop() : null;
		try {
			stopAndAwait(current, timeout, !graceful || commandFailure != null);
			state.set(ProcessState.STOPPED);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			terminateTree(current, true);
			state.set(ProcessState.FAILED);
			throw shutdownFailure("Interrupted while stopping process '" + name + "'", failure, commandFailure);
		} catch (ProcessException failure) {
			state.set(ProcessState.FAILED);
			if (commandFailure != null) failure.addSuppressed(commandFailure);
			throw failure;
		} finally {
			console.closeInput();
		}

		if (commandFailure != null) throw commandFailure;
	}

	private void awaitReadiness(ProcessExecution launched, Duration timeout) throws InterruptedException {
		if (!readiness.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
			state.set(ProcessState.FAILED);
			throw new ProcessException(name, "Process '" + name + "' did not become ready within " + timeout
					+ console.diagnosticTail());
		}
		if (launched.isAlive() && state.get() == ProcessState.READY) return;

		state.set(ProcessState.FAILED);
		throw new ProcessException(name, "Process '" + name + "' exited or ended output before becoming ready"
				+ console.diagnosticTail());
	}

	private void becameReady() {
		if (state.compareAndSet(ProcessState.STARTING, ProcessState.READY)) readiness.countDown();
	}

	private void outputEnded() {
		if (state.compareAndSet(ProcessState.STARTING, ProcessState.FAILED)) readiness.countDown();
	}

	private void exited() {
		state.updateAndGet(current -> current == ProcessState.STOPPING || current == ProcessState.STOPPED
				? current : ProcessState.FAILED);
		readiness.countDown();
		console.closeInput();
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
