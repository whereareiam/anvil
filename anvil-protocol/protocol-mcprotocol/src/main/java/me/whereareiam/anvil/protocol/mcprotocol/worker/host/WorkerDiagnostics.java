package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bounded process output retained for worker startup, request, and shutdown failures.
 */
final class WorkerDiagnostics {
	private static final int HISTORY_LIMIT = 80;
	private final Deque<String> lines = new ArrayDeque<>();

	synchronized void remember(@NotNull String line) {
		lines.addLast(line);
		while (lines.size() > HISTORY_LIMIT)
			lines.removeFirst();
	}

	synchronized @NotNull String tail() {
		if (lines.isEmpty()) return "";
		return "\nWorker output:\n" + String.join("\n", lines);
	}
}
