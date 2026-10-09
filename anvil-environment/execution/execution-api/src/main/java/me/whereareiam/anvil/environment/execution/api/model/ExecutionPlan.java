package me.whereareiam.anvil.environment.execution.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Ordered execution inputs prepared by the caller before managed process startup.
 * Provider policies and file or attachment implementations are supplied through separate boundaries.
 */
@Value
@Builder
public class ExecutionPlan {
	@NotNull String executionProviderId;
	@NotNull ExecutionContext context;
	@NotNull @Singular("process") List<ProcessSpec> processes;
	/**
	 * Resolved positive startup and shutdown deadlines for each process.
	 */
	@NotNull ProcessTimeouts processTimeouts;

	/**
	 * Resolved positive limits applied within each preparation or bulk-start operation.
	 */
	@NotNull ProcessScheduling processScheduling;
}
