package me.whereareiam.anvil.engine.scenario.process.slot;

import lombok.Builder;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.engine.process.ManagedProcess;
import me.whereareiam.anvil.engine.scenario.process.ScenarioAgent;
import me.whereareiam.anvil.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.execution.api.model.JavaCommand;
import me.whereareiam.anvil.engine.process.type.ManagedProxy;
import me.whereareiam.anvil.engine.process.type.ManagedServer;
import me.whereareiam.anvil.engine.provisioning.workspace.WorkspaceFiles;
import me.whereareiam.anvil.engine.provisioning.workspace.WorkspaceSession;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * Owns one declared process and its prepared workspace across execution generations.
 * Only the owning scenario session initiates replacement or finalization.
 */
public final class ProcessSlot {
	private final @NotNull EngineOptions options;
	private final @NotNull MinecraftProcess declaration;
	private final @NotNull PlatformProvider provider;
	private final @NotNull PlatformContext context;
	private final @NotNull WorkspacePlan workspacePlan;
	private final @NotNull AgentConnectionProvider agentConnections;
	private final @NotNull ProcessTarget target;

	private final ScenarioAgent agent = new ScenarioAgent(null);
	private @Nullable WorkspaceSession workspace;
	private @Nullable ManagedProcess current;
	private @Nullable JavaCommand command;

	@Builder
	private ProcessSlot(
			@NotNull EngineOptions options,
			@NotNull MinecraftProcess declaration,
			@NotNull PlatformProvider provider,
			@NotNull PlatformContext context,
			@NotNull WorkspacePlan workspacePlan,
			@NotNull AgentConnectionProvider agentConnections,
			@NotNull ProcessTarget target
	) {
		this.options = options;
		this.declaration = declaration;
		this.provider = provider;
		this.context = context;
		this.workspacePlan = workspacePlan;
		this.agentConnections = agentConnections;
		this.target = target;
	}

	/**
	 * Returns the declared process name used by the scenario registry.
	 *
	 * @return process name
	 */
	public @NotNull String name() {
		return declaration.getName();
	}

	/**
	 * Prepares the process workspace and resolves its launch command.
	 */
	public void prepare() {
		if (workspace != null) throw new IllegalStateException("Process is already prepared: " + declaration.getName());

		workspace = WorkspaceSession.prepare(options.getWorkDirectory(), context.getWorkDirectory(), workspacePlan,
				provider.defaultCaches(declaration), cacheIdentity(), options.getCacheDirectory(), new WorkspaceFiles());
		try {
			var distribution = provider.resolve(declaration, context);
			command = JavaCommand.builder()
					.jar(distribution.getJar())
					.memoryMegabytes(declaration.getMemoryMegabytes())
					.jvmArguments(declaration.getJvmArguments())
					.arguments(provider.programArguments(declaration))
					.build();
		} catch (IOException failure) {
			throw new ProvisioningException("Could not prepare process '" + declaration.getName() + "'", failure);
		}

	}

	/**
	 * Applies the platform configuration for the prepared process.
	 */
	public void configure() {
		try {
			provider.configure(declaration, context);
		} catch (IOException failure) {
			throw new ProvisioningException("Could not configure process '" + declaration.getName() + "'", failure);
		}
	}

	/**
	 * Starts the first execution generation.
	 */
	public void start() {
		launchGeneration();
	}

	/**
	 * Stops the current generation, refreshes its agent connection, and starts a replacement.
	 *
	 * @return replacement process generation
	 */
	public @NotNull ManagedProcess restart() {
		if (command == null || current == null)
			throw new IllegalStateException("Process has not completed preparation: " + declaration.getName());

		closeAgent();
		stopProcess();
		try {
			provider.configure(declaration, context);
		} catch (IOException failure) {
			throw new ProcessException(declaration.getName(), "Could not reconfigure process '" + declaration.getName() + "'", failure);
		}
		launchGeneration();

		return current();
	}

	/**
	 * Returns the currently running process generation.
	 *
	 * @return current process
	 * @throws IllegalStateException when the process has not started
	 */
	public @NotNull ManagedProcess current() {
		if (current == null) throw new IllegalStateException("Process has not started: " + declaration.getName());
		return current;
	}

	/**
	 * Returns the stable agent handle for this process when the platform supplies an agent.
	 *
	 * @return borrowed agent handle, or {@code null} when the platform has no agent
	 */
	public @Nullable AgentClient agent() {
		return provider.platformAgent() == null ? null : agent;
	}

	/**
	 * Closes the current platform-agent connection while retaining the stable handle for restarts.
	 */
	public void closeAgent() {
		agent.close();
	}

	/**
	 * Stops the current process generation if one has been started.
	 */
	public void stopProcess() {
		if (current != null) current.stop(options.getStopTimeout());
	}

	/**
	 * Releases the execution target and finalizes the process workspace.
	 *
	 * @param successful whether the scenario completed successfully
	 */
	public void finish(boolean successful) {
		try (target) {
			if (workspace != null) workspace.finish(successful);
		}
	}

	private void launchGeneration() {
		InetSocketAddress address = target.address();
		current = declaration instanceof MinecraftServer
				? new ManagedServer(declaration.getName(), address, context.getWorkDirectory())
				: new ManagedProxy(declaration.getName(), address, context.getWorkDirectory());
		AgentEndpoint endpoint = provider.platformAgent() == null ? null : agentEndpoint();
		Map<String, String> environment = endpoint == null ? Map.of()
				: Map.of("ANVIL_AGENT_PORT", Integer.toString(target.agentPort()), "ANVIL_AGENT_TOKEN", endpoint.token(),
						"ANVIL_AGENT_BIND", target.agentBindAddress());

		current.start(() -> target.start(command.toBuilder().environment(environment).build()), provider.readinessPattern(), provider.stopCommand(), context.getScenario().getStartupTimeout());
		if (endpoint != null) agent.replace(agentConnections.connect(endpoint.port(), endpoint.token(), Duration.ofSeconds(20)));
	}

	private AgentEndpoint agentEndpoint() {
		byte[] token = new byte[32];
		new SecureRandom().nextBytes(token);

		return new AgentEndpoint(target.agentAddress().getPort(), HexFormat.of().formatHex(token));
	}

	private String cacheIdentity() {
		Distribution distribution = declaration.getDistribution();
		return declaration.getName() + "|" + declaration.getPlatform() + "|"
				+ Objects.toString(distribution.getVersion(), "") + "|" + Objects.toString(distribution.getBuild(), "") + "|"
				+ Objects.toString(distribution.getSha256(), "") + "|" + Objects.toString(distribution.getLocalJar(), "");
	}

	private record AgentEndpoint(int port, String token) { }
}
