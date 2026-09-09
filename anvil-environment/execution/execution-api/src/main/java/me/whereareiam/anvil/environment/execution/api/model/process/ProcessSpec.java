package me.whereareiam.anvil.environment.execution.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

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
	@NotNull Pattern readinessPattern;
	@NotNull String stopCommand;
	int memoryMegabytes;
}
