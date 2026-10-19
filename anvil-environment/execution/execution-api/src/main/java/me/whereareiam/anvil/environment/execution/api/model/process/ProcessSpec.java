package me.whereareiam.anvil.environment.execution.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Execution location, readiness, resource budget, and startup dependencies for one process.
 * Dependency names refer to other entries in the same execution plan.
 */
@Value
@Builder
public class ProcessSpec {
	@NotNull ProcessRequest request;
	boolean proxy;
	@NotNull @Singular("dependency") Set<String> dependencies;
	/**
	 * Whether the process runs a container image rather than a Minecraft server or proxy.
	 */
	boolean container;
	/**
	 * The line the process prints once it is ready; null when it is ready once each of its listeners and ports
	 * reaches a listener.
	 */
	@Nullable Pattern readinessPattern;
	/**
	 * The console command that stops the process; null when it is stopped by its execution provider.
	 */
	@Nullable String stopCommand;
	int memoryMegabytes;
	/**
	 * The execution provider that runs the process; null for the plan's.
	 */
	@Nullable String executionProviderId;
}
