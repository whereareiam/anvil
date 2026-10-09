package me.whereareiam.anvil.launcher.assembly.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.ProcessAgentClient;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedLaunch;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedProcess;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.platform.api.PlatformPreparer;
import me.whereareiam.anvil.platform.api.model.PlatformRequest;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Retains prepared workspace ownership while creating fresh platform configuration for each generation.
 */
@RequiredArgsConstructor
final class PreparedPlatformProcess implements PreparedProcess {
	private final @NotNull ProcessPlan plan;
	private final @NotNull PlatformRequest request;
	private final @NotNull PlatformPreparer platforms;
	private final @NotNull PreparedWorkspace workspace;
	private final @NotNull JavaCommand command;
	private final @NotNull ProcessTarget target;
	private final @NotNull AgentConnectionProvider connections;
	private final @Nullable ProcessAgentClient agent;
	private final @Nullable Runnable ready;
	private final @Nullable CapabilityOwner<ProcessCapability> capabilities;

	@Override
	public @NotNull PreparedLaunch launch() {
		try {
			platforms.configure(plan, request);
		} catch (IOException failure) {
			String name = plan.getDeclaration().getName();
			throw new ProcessException(name, "Could not configure process '" + name + "'", failure);
		}

		return new ProcessLaunch(command, target, connections, agent, ready);
	}

	@Override
	public @Nullable CapabilityOwner<ProcessCapability> capabilities() {
		return capabilities;
	}

	@Override
	public void finish(boolean successful) {
		workspace.finish(successful);
	}
}
