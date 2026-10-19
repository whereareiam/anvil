package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.model.process.console.ConsoleLine;
import me.whereareiam.anvil.api.model.process.console.ConsoleOutput;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Process-local console writer and bounded output observer.
 */
final class ManagedProcessConsole implements ProcessConsole {
	private static final int HISTORY_LIMIT = 2000;

	private final String processName;
	private final Deque<Line> lines = new ArrayDeque<>();

	private @Nullable BufferedWriter writer;
	private volatile @Nullable Thread capture;
	private final Path logFile;
	private long sequence;
	private boolean closed;

	ManagedProcessConsole(@NotNull String processName, @NotNull Path workDirectory) {
		this.processName = processName;
		this.logFile = workDirectory.resolve("anvil-console.log");
	}

	void attach(
			@NotNull ProcessExecution process,
			@Nullable Pattern readinessPattern,
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
		this.capture = capture;
		capture.start();
	}

	/**
	 * Waits until the output of an exited process is captured, so that nothing is written to its
	 * workspace afterwards. Returns after the timeout when a surviving descendant keeps the output open.
	 */
	void awaitOutput(@NotNull Duration timeout) {
		Thread running = capture;
		if (running == null || running == Thread.currentThread()) return;

		try {
			running.join(timeout.toMillis());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	void append(@NotNull String text) {
		synchronized (lines) {
			lines.addLast(new Line(++sequence, text));

			while (lines.size() > HISTORY_LIMIT)
				lines.removeFirst();

			lines.notifyAll();
		}
	}

	/**
	 * Records a line of Anvil's own after the output was captured, in the history and in the workspace log, so
	 * retained diagnostics show what Anvil did to the process.
	 */
	void record(@NotNull String text) {
		append(text);
		try {
			Files.writeString(logFile, text + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException failure) {
			append("[Anvil] Failed to record to the console log: " + failure.getMessage());
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
	public @NotNull ConsoleWatch watch() {
		synchronized (lines) {
			return new Watch(sequence);
		}
	}

	@Override
	public @NotNull ConsoleWatch watchFromStart() {
		synchronized (lines) {
			return new Watch(lines.isEmpty() ? sequence : lines.getFirst().sequence() - 1);
		}
	}

	private @Nullable Line next(long after, Predicate<String> matcher) {
		for (Line line : lines)
			if (line.sequence() > after && matcher.test(line.text())) return line;

		return null;
	}

	private boolean evicted(long after) {
		return !lines.isEmpty() && after < lines.getFirst().sequence() - 1;
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

	private void capture(ProcessExecution process, @Nullable Pattern readinessPattern, Runnable onReady, Runnable onOutputEnded) {
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

				if (readinessPattern != null && readinessPattern.matcher(line).find()) onReady.run();
			}
		} catch (IOException failure) {
			append("[Anvil] Failed to capture process output: " + failure.getMessage());
		} finally {
			closeOutput();
			onOutputEnded.run();
		}
	}

	/**
	 * A position in this console's history that each wait or read moves forward.
	 */
	private final class Watch implements ConsoleWatch {
		private long position;

		private Watch(long position) {
			this.position = position;
		}

		@Override
		public @NotNull String printed(@NotNull Predicate<String> matcher, @NotNull Duration timeout) {
			if (timeout.isNegative() || timeout.isZero())
				throw new IllegalArgumentException("timeout must be positive");

			long deadline = System.nanoTime() + timeout.toNanos();
			synchronized (lines) {
				while (true) {
					if (evicted(position))
						throw failure("Console history after line " + position + " was evicted");

					Line line = next(position, matcher);
					if (line != null) {
						position = line.sequence();
						return line.text();
					}

					if (closed)
						throw failure("Process has no further console output while waiting for a matching line");

					long remaining = deadline - System.nanoTime();
					if (remaining <= 0)
						throw failure("Process did not output a matching line within " + timeout);

					waitForOutput(remaining);
				}
			}
		}

		@Override
		public void neverPrinted(@NotNull Predicate<String> matcher, @NotNull Duration duration) {
			long deadline = System.nanoTime() + duration.toNanos();
			synchronized (lines) {
				while (true) {
					Line line = next(position, matcher);
					if (line != null)
						throw failure("Process output a line it must not output: " + line.text());

					long remaining = deadline - System.nanoTime();
					if (closed || remaining <= 0) return;

					waitForOutput(remaining);
				}
			}
		}

		@Override
		public @NotNull ConsoleOutput read(int maximumLines) {
			if (maximumLines <= 0) throw new IllegalArgumentException("maximumLines must be positive");

			synchronized (lines) {
				boolean truncated = evicted(position);
				List<ConsoleLine> batch = lines.stream()
						.filter(line -> line.sequence() > position)
						.limit(maximumLines)
						.map(line -> new ConsoleLine(line.sequence(), line.text()))
						.toList();
				if (!batch.isEmpty()) position = batch.getLast().getSequence();

				return new ConsoleOutput(truncated, closed, batch);
			}
		}
	}

	private record Line(long sequence, String text) { }
}
