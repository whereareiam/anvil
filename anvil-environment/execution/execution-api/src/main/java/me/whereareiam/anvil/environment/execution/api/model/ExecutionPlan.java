package me.whereareiam.anvil.environment.execution.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Ordered execution inputs prepared by the caller before managed process startup.
 * Provider policies and file or attachment implementations are supplied through separate boundaries.
 */
@Value
@Builder
public class ExecutionPlan {
	@NotNull String executionId;
	@NotNull ExecutionContext context;
	@NotNull @Singular("process") List<ProcessSpec> processes;
	@NotNull Duration startupTimeout;
	@NotNull Duration stopTimeout;
	int parallelism;
	int startupMemoryMegabytes;
}
