package me.whereareiam.anvil.api.model.process.lifecycle;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.ProcessPriority;
import org.jetbrains.annotations.Nullable;

/**
 * Limits on how much of the machine launched processes use. Parallelism and the startup heap allowance
 * apply within one scenario preparation or bulk-start operation and do not form an aggregate budget
 * across active scenarios; omitted, they are selected from host capacity when the launcher assembles its
 * services. Processors and priority apply to every launched process.
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

	/**
	 * Processors each launched process may assume, which its JVM sizes thread pools and compilers from.
	 * Omitted, every process assumes all processors of the machine, so several starting at once ask for far
	 * more than there are. A process declaration can override the limit through its own JVM arguments.
	 */
	@Nullable Integer processors;

	/**
	 * Priority of launched processes; omitted means {@link ProcessPriority#NORMAL}. An execution provider
	 * that cannot lower the priority refuses {@link ProcessPriority#LOW}.
	 */
	@Nullable ProcessPriority priority;
}
