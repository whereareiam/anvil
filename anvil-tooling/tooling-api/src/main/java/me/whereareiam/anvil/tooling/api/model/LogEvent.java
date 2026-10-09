package me.whereareiam.anvil.tooling.api.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * One console line identified by session, process execution, and sequence.
 */
@Value
@Builder
@Jacksonized
public class LogEvent {
	/**
	 * Run identity that keeps replacement environments' output separate.
	 */
	@NotNull String sessionId;
	/**
	 * Stable process name within the run's scenario.
	 */
	@NotNull String process;
	/**
	 * Opaque identifier of the process execution that produced this value. It remains stable for that
	 * execution, changes on replacement, and is shared by snapshots and console events.
	 */
	@NotNull UUID executionId;
	/**
	 * Monotonic console sequence within this execution, used to detect gaps in retained output.
	 */
	long sequence;
	/**
	 * Original console line, including ANSI sequences emitted by the process.
	 */
	@NotNull String text;
}
