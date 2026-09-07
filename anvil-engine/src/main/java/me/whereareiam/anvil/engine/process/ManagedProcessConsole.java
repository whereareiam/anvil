package me.whereareiam.anvil.engine.process;

import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.process.ProcessConsole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.whereareiam.anvil.execution.api.process.ProcessExecution;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Process-local console writer and bounded output observer.
 */
final class ManagedProcessConsole implements ProcessConsole {
	private static final int HISTORY_LIMIT = 2000;

	private final String processName;
	private final Deque<Line> lines = new ArrayDeque<>();

	private @Nullable BufferedWriter writer;
	private final Path logFile;
	private long sequence;
	private boolean closed;

	ManagedProcessConsole(@NotNull String processName, @NotNull Path workDirectory) {
		this.processName = processName;
		this.logFile = workDirectory.resolve("anvil-console.log");
	}

	void attach(
			@NotNull ProcessExecution process,
			@NotNull Pattern readinessPattern,
			@NotNull Runnable onReady,
			@NotNull Runnable onOutputEnded
	) {
		synchronized (this) {
			writer = new BufferedWriter(new OutputStreamWriter(process.input(), StandardCharsets.UTF_8));
		}

		Thread capture = new Thread(
				() -> capture(process, readinessPattern, onReady, onOutputEnded),
				"anvil-" + processName + "-output"
		);
		capture.setDaemon(true);
		capture.start();
	}

	void append(@NotNull String text) {
		synchronized (lines) {
			lines.addLast(new Line(++sequence, text));

			while (lines.size() > HISTORY_LIMIT)
				lines.removeFirst();

			lines.notifyAll();
		}
	}

	void closeOutput() {
		synchronized (lines) {
			closed = true;
			lines.notifyAll();
		}
	}

	@Override
	public synchronized void sendCommand(@NotNull String command) {
		BufferedWriter current = writer;
		if (current == null)
			throw new ProcessException(processName, "Process '" + processName + "' has no active console");

		try {
			current.write(command);
			current.newLine();
			current.flush();
		} catch (IOException failure) {
			throw new ProcessException(processName, "Could not write to process '" + processName + "'", failure);
		}
	}

	@Override
	public @NotNull List<String> tail(int maximumLines) {
		if (maximumLines < 0)
			throw new IllegalArgumentException("maximumLines must not be negative");

		synchronized (lines) {
			List<String> copy = lines.stream()
					.map(Line::text)
					.toList();
			int from = Math.max(0, copy.size() - maximumLines);

			return List.copyOf(copy.subList(from, copy.size()));
		}
	}

	@Override
	public long checkpoint() {
		synchronized (lines) {
			return sequence;
		}
	}

	@Override
	public @NotNull String await(@NotNull String text, long after, @NotNull Duration timeout) {
		if (text.isEmpty())
			throw new IllegalArgumentException("text must not be empty");
		if (timeout.isNegative() || timeout.isZero())
			throw new IllegalArgumentException("timeout must be positive");

		long deadline = System.nanoTime() + timeout.toNanos();
		synchronized (lines) {
			validateCheckpoint(after);

			while (true) {
				if (!lines.isEmpty() && after < lines.getFirst().sequence() - 1)
					throw failure("Console history after checkpoint " + after + " was evicted");

				for (Line line : lines) {
					if (line.sequence() > after && line.text().contains(text))
						return line.text();
				}

				if (closed)
					throw failure("Process has no further console output while waiting for '" + text + "'");

				long remaining = deadline - System.nanoTime();
				if (remaining <= 0)
					throw failure("Process did not output '" + text + "' after checkpoint " + after
							+ " within " + timeout);

				waitForOutput(remaining);
			}
		}
	}

	private void validateCheckpoint(long checkpoint) {
		if (checkpoint < 0 || checkpoint > sequence)
			throw new IllegalArgumentException("Checkpoint is outside this process's console history");
	}

	private void waitForOutput(long remaining) {
		try {
			TimeUnit.NANOSECONDS.timedWait(lines, remaining);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new ProcessException(
					processName,
					"Interrupted while waiting for console output from '" + processName + "'",
					failure
			);
		}
	}

	private ProcessException failure(String message) {
		List<String> tail = tail(30);
		if (tail.isEmpty())
			return new ProcessException(processName, message);

		return new ProcessException(processName, message + "\nLast output:\n" + String.join("\n", tail));
	}

	synchronized void closeInput() {
		if (writer == null) return;

		try {
			writer.close();
		} catch (IOException failure) {
			append("[Anvil] Failed to close process writer: " + failure.getMessage());
		} finally {
			writer = null;
		}
	}

	@NotNull String diagnosticTail() {
		List<String> tail = tail(30);
		return tail.isEmpty() ? "" : "\nLast output:\n" + String.join("\n", tail);
	}

	private void capture(ProcessExecution process, Pattern readinessPattern, Runnable onReady, Runnable onOutputEnded) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.output(), StandardCharsets.UTF_8));
		     BufferedWriter log = Files.newBufferedWriter(
				     logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			String line;
			while ((line = reader.readLine()) != null) {
				append(line);
				log.write(line);
				log.newLine();
				log.flush();

				if (readinessPattern.matcher(line).find()) onReady.run();
			}
		} catch (IOException failure) {
			append("[Anvil] Failed to capture process output: " + failure.getMessage());
		} finally {
			closeOutput();
			onOutputEnded.run();
		}
	}

	private record Line(long sequence, String text) { }
}
