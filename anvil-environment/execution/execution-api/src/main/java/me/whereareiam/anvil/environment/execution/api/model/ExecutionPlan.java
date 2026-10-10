package me.whereareiam.anvil.environment.execution.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

/**
 * Ordered execution inputs prepared by the caller before managed process startup.
 * Provider policies and file or attachment implementations are supplied through separate boundaries.
 */
@Value
@Builder(toBuilder = true)
public class ExecutionPlan {
	@NotNull String executionProviderId;
	@NotNull ExecutionContext context;
	@NotNull @Singular("process") List<ProcessSpec> processes;
	/**
	 * Game addresses of running processes outside this plan that its processes connect to, by process name.
	 * They are supplied to preparation together with the addresses of the planned processes.
	 */
	@NotNull @Singular("peer") Map<String, InetSocketAddress> peers;
	/**
	 * Resolved positive startup and shutdown deadlines for each process.
	 */
	@NotNull ProcessTimeouts processTimeouts;

	/**
	 * Resolved positive limits applied within each preparation or bulk-start operation.
	 */
	@NotNull ProcessScheduling processScheduling;
}
