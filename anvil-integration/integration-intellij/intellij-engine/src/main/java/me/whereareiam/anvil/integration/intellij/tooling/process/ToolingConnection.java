package me.whereareiam.anvil.integration.intellij.tooling.process;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns a preparation child followed by a runner, including its command input and termination.
 */
@RequiredArgsConstructor
public final class ToolingConnection implements AutoCloseable {
	private final @NotNull Starter starter;
	private final @NotNull Executor executor;
	private final AtomicBoolean stopping = new AtomicBoolean();
	// Separate from the monitor: a write blocked on a hung runner must not prevent its termination.
	private final ReentrantLock writes = new ReentrantLock();
	private volatile @Nullable Process child;
	private volatile @Nullable BufferedWriter input;
	private volatile @Nullable StreamReader errors;

	public boolean isStopping() {
		return stopping.get();
	}

	public synchronized @NotNull Process start(@NotNull ProcessBuilder builder) throws IOException {
		if (isStopping()) throw new IOException("Anvil launch cancelled.");

		Process process = starter.start(builder);
		child = process;
		if (isStopping()) {
			forceTermination(process);
			throw new IOException("Anvil launch cancelled.");
		}

		return process;
	}

	public synchronized @NotNull Process startRunner(@NotNull ProcessBuilder builder) throws IOException {
		Process runner = start(builder);
		input = new BufferedWriter(new OutputStreamWriter(
				runner.getOutputStream(),
				StandardCharsets.UTF_8
		));

		return runner;
	}

	/**
	 * Reads merged preparation output on the controlling worker until the child exits.
	 *
	 * @param process preparation child owned by this connection
	 * @param output consumer for each output line
	 * @return preparation process exit code
	 */
	public int readPreparation(@NotNull Process process, @NotNull Consumer<String> output)
			throws IOException, InterruptedException {
		readLines(process.getInputStream(), output);
		return process.waitFor();
	}

	/**
	 * Reads protocol stdout on the controlling worker and stderr concurrently.
	 * Closing the connection waits for stderr delivery and releases its reader, including after failures.
	 *
	 * @param process runner owned by this connection
	 * @param output consumer for each protocol frame
	 * @param diagnostics consumer for each diagnostic line
	 * @param failureHandler reports an unexpected diagnostic-reader failure before shutdown begins
	 * @return runner exit code
	 */
	public int readRunner(
			@NotNull Process process,
			@NotNull Consumer<String> output,
			@NotNull Consumer<String> diagnostics,
			@NotNull Consumer<Throwable> failureHandler
	) throws IOException, InterruptedException {
		StreamReader reader = new StreamReader(process.getErrorStream(), diagnostics, failureHandler);
		errors = reader;
		reader.start();
		readLines(process.getInputStream(), output);
		return process.waitFor();
	}

	/**
	 * Stops the child and waits for output delivery before releasing its readers.
	 * Call from the controlling worker after output reading returns or fails.
	 *
	 * @throws IOException when reading or releasing the diagnostic stream fails
	 */
	@Override
	public void close() throws IOException {
		try (StreamReader reader = errors) {
			awaitTermination();
		}
	}

	public void send(@NotNull String request) throws IOException {
		writes.lock();
		try {
			BufferedWriter writer = input;
			if (writer == null || isStopping()) throw new IOException("Anvil is not ready for commands.");

			writer.write(request);
			writer.newLine();
			writer.flush();
		} finally {
			writes.unlock();
		}
	}

	public void stop() {
		if (!stopping.compareAndSet(false, true)) return;
		try {
			executor.execute(this::terminate);
		} catch (RuntimeException | Error failure) {
			Process process = child;
			try {
				if (process != null && process.isAlive()) forceTermination(process);
			} catch (RuntimeException | Error terminationFailure) {
				if (failure != terminationFailure) failure.addSuppressed(terminationFailure);
			}
			throw failure;
		}
	}

	public void awaitTermination() {
		Process process = child;
		if (process == null || !process.isAlive()) return;

		try {
			stop();
		} finally {
			awaitExit(process);
		}
	}

	private static void awaitExit(@NotNull Process process) {
		boolean interrupted = false;
		try {
			while (process.isAlive()) {
				try {
					process.waitFor();
				} catch (InterruptedException failure) {
					interrupted = true;
					forceTermination(process);
				}
			}
		} finally {
			if (interrupted) Thread.currentThread().interrupt();
		}
	}

	private void terminate() {
		Process process = child;
		BufferedWriter writer = input;
		if (process == null) return;

		try {
			requestTermination(process, writer);
			if (process.waitFor(30, TimeUnit.SECONDS)) return;

			process.destroy();
			process.waitFor(10, TimeUnit.SECONDS);
		} catch (IOException failure) {
			process.destroy();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			process.destroy();
		} finally {
			if (process.isAlive()) forceTermination(process);
		}
	}

	/**
	 * Closes the runner's input so it can stop gracefully. A write still blocked after a short wait means
	 * the runner is not reading; destroying it breaks the pipe and releases that write.
	 */
	private void requestTermination(@NotNull Process process, @Nullable BufferedWriter writer)
			throws IOException, InterruptedException {
		if (writer == null || !writes.tryLock(1, TimeUnit.SECONDS)) {
			process.destroy();
			return;
		}

		try {
			writer.close();
		} finally {
			writes.unlock();
		}
	}

	private static void forceTermination(Process process) {
		try {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
		} catch (UnsupportedOperationException ignored) {
			// In-memory process fixtures may not expose native process handles.
		} finally {
			process.destroyForcibly();
		}
	}

	private static void readLines(@NotNull InputStream stream, @NotNull Consumer<String> output) throws IOException {
		try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			readLines(reader, output);
		}
	}

	private static void readLines(@NotNull BufferedReader reader, @NotNull Consumer<String> output) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) output.accept(line);
	}

	private final class StreamReader implements Runnable, AutoCloseable {
		private final @NotNull BufferedReader reader;
		private final @NotNull Consumer<String> output;
		private final @NotNull Consumer<Throwable> failureHandler;
		private final @NotNull CompletableFuture<Void> completed = new CompletableFuture<>();

		private StreamReader(
				@NotNull InputStream stream,
				@NotNull Consumer<String> output,
				@NotNull Consumer<Throwable> failureHandler
		) {
			reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			this.output = output;
			this.failureHandler = failureHandler;
		}

		private void start() {
			try {
				executor.execute(this);
			} catch (RuntimeException | Error failure) {
				completed.completeExceptionally(failure);
				throw failure;
			}
		}

		@Override
		public void run() {
			try {
				readLines(reader, output);
				completed.complete(null);
			} catch (IOException | RuntimeException | Error failure) {
				if (isStopping() && failure instanceof IOException) {
					completed.complete(null);
					return;
				}

				try {
					failureHandler.accept(failure);
				} catch (RuntimeException | Error reportingFailure) {
					if (failure != reportingFailure) failure.addSuppressed(reportingFailure);
				}
				try {
					stop();
				} catch (RuntimeException | Error stoppingFailure) {
					if (failure != stoppingFailure) failure.addSuppressed(stoppingFailure);
				}
				completed.completeExceptionally(failure);
			}
		}

		@Override
		public void close() throws IOException {
			try (BufferedReader owned = reader) {
				completed.join();
			} catch (CompletionException failure) {
				Throwable cause = failure.getCause();
				for (Throwable suppressed : failure.getSuppressed())
					if (cause != suppressed) cause.addSuppressed(suppressed);
				if (cause instanceof IOException io) throw io;
				if (cause instanceof RuntimeException runtime) throw runtime;
				if (cause instanceof Error error) throw error;
				throw new IOException("Could not read tooling diagnostics", cause);
			}
		}
	}

	public interface Starter {
		@NotNull Process start(@NotNull ProcessBuilder builder) throws IOException;
	}
}
