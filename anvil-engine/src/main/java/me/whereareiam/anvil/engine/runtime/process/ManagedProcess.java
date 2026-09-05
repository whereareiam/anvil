package me.whereareiam.anvil.engine.runtime.process;

import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.api.runtime.RunningProcess;
import me.whereareiam.anvil.engine.AnvilException;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Shared process-backed runtime for a Minecraft server or proxy.
 */
public abstract class ManagedProcess implements RunningProcess {
	private static final int LOG_HISTORY_LIMIT = 2000;

	private final String name;
	private final InetSocketAddress address;
	private final Path workDirectory;
	private final AtomicReference<ProcessState> state = new AtomicReference<>(ProcessState.CREATED);
	private final Deque<String> logs = new ArrayDeque<>();
	private final CountDownLatch ready = new CountDownLatch(1);
	private final CountDownLatch exited = new CountDownLatch(1);
	private volatile Process process;
	private volatile BufferedWriter console;
	private volatile String stopCommand = "stop";

	/**
	 * Creates a not-yet-started managed process.
	 */
	protected ManagedProcess(String name, InetSocketAddress address, Path workDirectory) {
		this.name = name;
		this.address = address;
		this.workDirectory = workDirectory;
	}

	/**
	 * Starts the process and blocks until its readiness line is observed.
	 */
	public void start(
			List<String> command,
			Map<String, String> environment,
			Pattern readinessPattern,
			String stopCommand,
			Duration timeout
	) {
		if (!state.compareAndSet(ProcessState.CREATED, ProcessState.STARTING))
			throw new AnvilException("Process '" + name + "' cannot start from state " + state.get());

		this.stopCommand = stopCommand;
		try {
			Files.createDirectories(workDirectory);
			ProcessBuilder builder = new ProcessBuilder(command)
					.directory(workDirectory.toFile())
					.redirectErrorStream(true);
			builder.environment().putAll(environment);
			process = builder.start();
			console = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

			Thread output = new Thread(() -> captureOutput(readinessPattern), "anvil-" + name + "-output");
			output.setDaemon(true);
			output.start();

			process.onExit().thenRun(() -> {
				if (state.get() != ProcessState.STOPPING && state.get() != ProcessState.STOPPED)
					state.set(ProcessState.FAILED);
				exited.countDown();
				ready.countDown();
			});

			if (!ready.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				state.set(ProcessState.FAILED);
				throw new AnvilException("Process '" + name + "' did not become ready within " + timeout
						+ diagnosticTail());
			}
			if (!process.isAlive() || state.get() == ProcessState.FAILED)
				throw new AnvilException("Process '" + name + "' exited before becoming ready" + diagnosticTail());
		} catch (IOException e) {
			state.set(ProcessState.FAILED);
			throw new AnvilException("Could not start process '" + name + "' using " + command, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			state.set(ProcessState.FAILED);
			throw new AnvilException("Interrupted while starting process '" + name + "'", e);
		}
	}

	/**
	 * Stops the process gracefully, escalating to process-tree termination after the timeout.
	 */
	public void stop(Duration timeout) {
		Process current = process;
		if (current == null || !current.isAlive()) {
			state.set(ProcessState.STOPPED);
			return;
		}

		state.set(ProcessState.STOPPING);
		try {
			RuntimeException consoleFailure = null;
			try {
				sendCommand(stopCommand);
			} catch (RuntimeException exception) {
				consoleFailure = exception;
			}
			if (consoleFailure != null || !exited.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				current.descendants().forEach(ProcessHandle::destroy);
				current.destroy();
			}
			if (!current.waitFor(Math.max(1000, timeout.toMillis() / 3), TimeUnit.MILLISECONDS)) {
				current.descendants().forEach(ProcessHandle::destroyForcibly);
				current.destroyForcibly();
				if (!current.waitFor(Math.max(1000, timeout.toMillis() / 3), TimeUnit.MILLISECONDS)) {
					state.set(ProcessState.FAILED);
					throw new AnvilException("Process '" + name + "' did not exit after forced termination");
				}
			}
			state.set(ProcessState.STOPPED);
			if (consoleFailure != null)
				throw consoleFailure;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			current.descendants().forEach(ProcessHandle::destroyForcibly);
			current.destroyForcibly();
			state.set(ProcessState.FAILED);
			throw new AnvilException("Interrupted while stopping process '" + name + "'", e);
		}
	}

	private void captureOutput(Pattern readinessPattern) {
		Path logFile = workDirectory.resolve("anvil-console.log");
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
			 BufferedWriter file = Files.newBufferedWriter(
					 logFile,
					 StandardCharsets.UTF_8,
					 StandardOpenOption.CREATE,
					 StandardOpenOption.APPEND)) {
			String line;
			while ((line = reader.readLine()) != null) {
				synchronized (logs) {
					logs.addLast(line);
					while (logs.size() > LOG_HISTORY_LIMIT)
						logs.removeFirst();
				}
				file.write(line);
				file.newLine();
				file.flush();

				if (readinessPattern.matcher(line).find()
						&& state.compareAndSet(ProcessState.STARTING, ProcessState.READY))
					ready.countDown();
			}
		} catch (IOException e) {
			synchronized (logs) {
				logs.addLast("[Anvil] Failed to capture process output: " + e.getMessage());
			}
		}
	}

	private String diagnosticTail() {
		List<String> tail = logs(30);
		return tail.isEmpty() ? "" : "\nLast output:\n" + String.join("\n", tail);
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
	public void sendCommand(@NotNull String command) {
		BufferedWriter writer = console;
		if (writer == null)
			throw new AnvilException("Process '" + name + "' has no active console");

		try {
			writer.write(command);
			writer.newLine();
			writer.flush();
		} catch (IOException e) {
			throw new AnvilException("Could not write to process '" + name + "'", e);
		}
	}

	@Override
	public @NotNull List<String> logs(int maximumLines) {
		if (maximumLines < 0)
			throw new IllegalArgumentException("maximumLines must not be negative");

		synchronized (logs) {
			List<String> copy = new ArrayList<>(logs);
			int from = Math.max(0, copy.size() - maximumLines);
			return List.copyOf(copy.subList(from, copy.size()));
		}
	}

}
