package me.whereareiam.anvil.environment.execution.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.type.ProcessPriority;
import org.jetbrains.annotations.NotNull;

/**
 * What the engine decides about a scenario's execution sessions: its priority, download policy and network
 * policy. It holds no service of a machine, so it travels unchanged to a workbench on another machine, whose own
 * {@link me.whereareiam.anvil.environment.execution.api.ExecutionServices services} its sessions open with.
 */
@Value
@Builder(toBuilder = true)
public class ExecutionContext {
	/**
	 * Priority the processes of the environment run with. A provider that cannot lower the priority refuses
	 * {@link ProcessPriority#LOW} when the environment is opened.
	 */
	@NotNull
	@Builder.Default
	ProcessPriority processPriority = ProcessPriority.NORMAL;
	boolean offline;
	boolean refresh;

	@NotNull
	@Builder.Default
	NetworkPolicy networkPolicy = NetworkPolicy.builder().build();
}
