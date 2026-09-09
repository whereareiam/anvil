package me.whereareiam.anvil.launcher.assembly.execution;

import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.agent.client.AgentSession;
import me.whereareiam.anvil.agent.client.ProcessAgentClient;
import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedLaunch;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Connects execution addresses and generation callbacks to an agent-owned session.
 */
final class ProcessLaunch implements PreparedLaunch {
	private final @NotNull JavaCommand command;
	private final @Nullable AgentSession agent;

	ProcessLaunch(
			@NotNull JavaCommand command,
			@NotNull ProcessTarget target,
			@NotNull AgentConnectionProvider connections,
			@Nullable ProcessAgentClient client
	) {
		if (client == null) {
			agent = null;
			this.command = command;
			return;
		}

		agent = AgentSession.builder()
				.client(client)
				.connections(connections)
				.bindPort(target.agentPort())
				.bindAddress(target.agentBindAddress())
				.connectPort(target.agentAddress().getPort())
				.build();
		this.command = command.toBuilder().environment(agent.environment()).build();
	}

	@Override
	public @NotNull JavaCommand command() {
		return command;
	}

	@Override
	public void started() {
		if (agent != null) agent.connect();
	}

	@Override
	public void close() {
		if (agent != null) agent.close();
	}
}
