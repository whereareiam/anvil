package me.whereareiam.anvil.launcher.assembly.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.agent.client.ProcessAgentClient;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
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

	private boolean configured;

	@Override
	public @NotNull PreparedLaunch launch() {
		try {
			platforms.configure(plan, request);
		} catch (IOException failure) {
			String name = plan.getDeclaration().getName();
			if (configured) throw new ProcessException(name, "Could not reconfigure process '" + name + "'", failure);
			throw new ProvisioningException("Could not configure process '" + name + "'", failure);
		}
		configured = true;

		return new ProcessLaunch(command, target, connections, agent);
	}

	@Override
	public void finish(boolean successful) {
		workspace.finish(successful);
	}
}
