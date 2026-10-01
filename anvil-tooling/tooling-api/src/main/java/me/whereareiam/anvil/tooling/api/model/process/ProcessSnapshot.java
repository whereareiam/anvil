package me.whereareiam.anvil.tooling.api.model.process;

import lombok.Builder;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Immediate view of a scenario process and its join address.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ProcessSnapshot {
	/**
	 * Stable process name used for command routing and matching its declaration.
	 */
	@NotNull String name;
	/**
	 * Opaque identifier of the process execution that produced this value. It remains stable for that
	 * execution, changes on replacement, and is shared by snapshots and console events.
	 */
	@NotNull UUID executionId;
	/**
	 * Human-readable label derived from optional declaration metadata.
	 */
	@NotNull String displayName;
	/**
	 * Current process-execution lifecycle state supplied by the runtime.
	 */
	@NotNull ProcessState state;
	/**
	 * Host part of this execution's player-facing listener address.
	 */
	@NotNull String host;
	/**
	 * Allocated player-facing listener port.
	 */
	int port;
	/**
	 * Process workspace path for navigation and diagnostics; cleanup may later remove it.
	 */
	@NotNull String workDirectory;
}
