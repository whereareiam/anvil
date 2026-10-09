package me.whereareiam.anvil.runner.session;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.runner.scenario.DisplayNameResolver;
import me.whereareiam.anvil.api.model.process.console.ConsoleOutput;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.tooling.api.model.LogEvent;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Owns console cursors, process generations, and bounded retained output for one runner session. */
public final class SessionConsole {
	private static final int PENDING_LOG_LIMIT = 4000;
	private final Supplier<String> sessionId;
	private final Object monitor = new Object();
	private final Set<UUID> observedExecutions = new HashSet<>();
	private final Map<String, ConsoleCursor> consoles = new LinkedHashMap<>();
	private final List<LogEvent> pendingLogs = new ArrayList<>();
	private final List<ConsoleCursor> retiredConsoles = new ArrayList<>();
	private Map<String, PresentationMetadata> metadata = Map.of();

	public SessionConsole(@NotNull Supplier<String> sessionId) { this.sessionId = sessionId; }

	public void beginRun(@NotNull Map<String, PresentationMetadata> metadata) {
		synchronized (monitor) {
			pendingLogs.addAll(drainLogs());
			boundPendingLogs();
			consoles.clear();
			observedExecutions.clear();
			retiredConsoles.clear();
			this.metadata = Map.copyOf(metadata);
		}
	}

	public void processCreated(@NotNull RunningProcess process) {
		synchronized (monitor) {
			ConsoleCursor cursor = consoles.get(process.name());
			UUID executionId = process.executionId();
			if (!observedExecutions.add(executionId)) return;
			if (cursor != null) retiredConsoles.add(cursor);
			consoles.put(process.name(), new ConsoleCursor(sessionId.get(), process, executionId));
		}
	}

	public @NotNull List<ProcessSnapshot> descriptors() {
		synchronized (monitor) {
			return consoles.values().stream().map(cursor -> {
				RunningProcess process = cursor.process;
				return ProcessSnapshot.builder().name(process.name()).executionId(process.executionId())
						.displayName(DisplayNameResolver.resolve(process.name(), metadata.get(process.name())))
						.state(me.whereareiam.anvil.tooling.api.type.ProcessState.fromWireValue(process.state().name())).host(process.address().getHostString()).port(process.address().getPort())
						.workDirectory(process.workDirectory().toString()).build();
			}).toList();
		}
	}

	public @NotNull List<String> tail(@NotNull String process, int maximumLines) {
		synchronized (monitor) {
			ConsoleCursor cursor = consoles.get(process);
			if (cursor == null) throw new NoSuchElementException("Unknown process: " + process);
			return cursor.process.console().tail(maximumLines);
		}
	}

	public @NotNull List<LogEvent> drain() {
		synchronized (monitor) { return drainLogs(); }
	}

	private @NotNull List<LogEvent> drainLogs() {
		List<LogEvent> events = new ArrayList<>(pendingLogs);
		pendingLogs.clear();
		Stream.concat(retiredConsoles.stream(), consoles.values().stream()).forEach(cursor -> {
			ConsoleOutput output = cursor.process.console().read(cursor.checkpoint, 2000);
			if (output.isTruncated()) events.add(cursor.event(cursor.checkpoint, "[Anvil] Earlier console output was evicted from bounded history."));
			output.getLines().forEach(line -> events.add(cursor.event(line.getSequence(), line.getText())));
			cursor.checkpoint = output.getNextCheckpoint();
			cursor.drained = output.isClosed() && output.getLines().size() < 2000;
		});
		retiredConsoles.removeIf(cursor -> cursor.drained);
		return List.copyOf(events);
	}

	private void boundPendingLogs() {
		if (pendingLogs.size() <= PENDING_LOG_LIMIT) return;
		LogEvent lastEvicted = pendingLogs.get(pendingLogs.size() - PENDING_LOG_LIMIT);
		pendingLogs.subList(0, pendingLogs.size() - PENDING_LOG_LIMIT + 1).clear();
		pendingLogs.addFirst(LogEvent.builder().sessionId(lastEvicted.getSessionId()).process(lastEvicted.getProcess())
				.executionId(lastEvicted.getExecutionId()).sequence(lastEvicted.getSequence())
				.text("[Anvil] Earlier undelivered session output was evicted from bounded history.").build());
	}

	private static final class ConsoleCursor {
		private final String sessionId;
		private final RunningProcess process;
		private final UUID executionId;
		private boolean drained;
		private long checkpoint;
		private ConsoleCursor(String sessionId, RunningProcess process, UUID executionId) {
			this.sessionId = sessionId; this.process = process; this.executionId = executionId;
		}
		private LogEvent event(long sequence, String text) { return LogEvent.builder().sessionId(sessionId).process(process.name())
				.executionId(executionId).sequence(sequence).text(text).build(); }
	}
}
