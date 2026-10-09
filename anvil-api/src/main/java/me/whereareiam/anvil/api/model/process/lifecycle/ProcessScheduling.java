package me.whereareiam.anvil.api.model.process.lifecycle;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * Scheduling limits applied within one scenario preparation or bulk-start operation.
 * These limits do not form an aggregate budget across active scenarios. Omitted engine
 * values are selected from host capacity when the launcher assembles its services.
 */
@Value
@Builder(toBuilder = true)
public class ProcessScheduling {
	/**
	 * Maximum independent preparation or startup operations running concurrently.
	 */
	@Nullable Integer parallelism;

	/**
	 * Declared heap allowance for concurrent starts, in MiB. A process larger than the
	 * allowance starts alone. This does not limit memory used by processes already running.
	 */
	@Nullable Integer startupMemoryMegabytes;
}
