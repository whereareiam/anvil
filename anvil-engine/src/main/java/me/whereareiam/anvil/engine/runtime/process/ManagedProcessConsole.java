package me.whereareiam.anvil.engine.runtime.process;

import me.whereareiam.anvil.api.runtime.ProcessConsole;
import me.whereareiam.anvil.engine.AnvilException;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Process-local console writer and bounded output observer.
 */
final class ManagedProcessConsole implements ProcessConsole {
	private static final int HISTORY_LIMIT = 2000;

	private final String processName;
	private final Deque<Line> lines = new ArrayDeque<>();

	private BufferedWriter writer;
	private long sequence;
	private boolean closed;

	ManagedProcessConsole(@NotNull String processName) {
		this.processName = processName;
	}

	void writer(@NotNull BufferedWriter writer) {
		this.writer = writer;
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
	public void sendCommand(@NotNull String command) {
		BufferedWriter current = writer;
		if (current == null)
			throw new AnvilException("Process '" + processName + "' has no active console");

		try {
			current.write(command);
			current.newLine();
			current.flush();
		} catch (IOException failure) {
			throw new AnvilException("Could not write to process '" + processName + "'", failure);
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
			throw new AnvilException(
					"Interrupted while waiting for console output from '" + processName + "'",
					failure
			);
		}
	}

	private AnvilException failure(String message) {
		List<String> tail = tail(30);
		if (tail.isEmpty())
			return new AnvilException(message);

		return new AnvilException(message + "\nLast output:\n" + String.join("\n", tail));
	}

	private record Line(long sequence, String text) { }
}
