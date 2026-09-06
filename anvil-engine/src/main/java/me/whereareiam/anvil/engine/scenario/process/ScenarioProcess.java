package me.whereareiam.anvil.engine.scenario.process;

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
import me.whereareiam.anvil.engine.process.PortSelection;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns one declared process and its prepared workspace across execution generations.
 * Only the owning scenario run initiates replacement or finalization.
 */
public final class ScenarioProcess {
	private final @NotNull EngineOptions options;
	private final @NotNull MinecraftProcess declaration;
	private final @NotNull PlatformProvider provider;
	private final @NotNull PlatformContext context;
	private final @NotNull WorkspacePlan workspacePlan;
	private final @NotNull AgentConnectionProvider agentConnections;
	private final @NotNull PortSelection ports;

	private final ScenarioAgent agent = new ScenarioAgent(null);
	private @Nullable WorkspaceSession workspace;
	private @Nullable ManagedProcess current;
	private @Nullable List<String> command;

	@Builder
	private ScenarioProcess(
			@NotNull EngineOptions options,
			@NotNull MinecraftProcess declaration,
			@NotNull PlatformProvider provider,
			@NotNull PlatformContext context,
			@NotNull WorkspacePlan workspacePlan,
			@NotNull AgentConnectionProvider agentConnections,
			@NotNull PortSelection ports
	) {
		this.options = options;
		this.declaration = declaration;
		this.provider = provider;
		this.context = context;
		this.workspacePlan = workspacePlan;
		this.agentConnections = agentConnections;
		this.ports = ports;
	}

	@NotNull String name() {
		return declaration.getName();
	}

	void start() {
		if (workspace != null) throw new IllegalStateException("Process is already prepared: " + declaration.getName());

		workspace = WorkspaceSession.prepare(options.getWorkDirectory(), context.getWorkDirectory(), workspacePlan,
				provider.defaultCaches(declaration), cacheIdentity(), options.getCacheDirectory(), new WorkspaceFiles());
		try {
			var distribution = provider.resolve(declaration, context);
			List<String> arguments = new ArrayList<>();
			arguments.add(context.getJavaExecutable().toString());
			arguments.add("-Xms256m");
			arguments.add("-Xmx" + declaration.getMemoryMegabytes() + "m");
			arguments.addAll(declaration.getJvmArguments());
			arguments.add("-jar");
			arguments.add(distribution.getJar().toAbsolutePath().toString());
			arguments.addAll(provider.programArguments(declaration));
			command = List.copyOf(arguments);
			provider.configure(declaration, context);
		} catch (IOException failure) {
			throw new ProvisioningException("Could not prepare process '" + declaration.getName() + "'", failure);
		}

		launchGeneration();
	}

	ManagedProcess restart() {
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

	@NotNull ManagedProcess current() {
		if (current == null) throw new IllegalStateException("Process has not started: " + declaration.getName());
		return current;
	}

	@Nullable AgentClient agent() {
		return provider.platformAgent() == null ? null : agent;
	}

	void closeAgent() {
		agent.close();
	}

	void stopProcess() {
		if (current != null) current.stop(options.getStopTimeout());
	}

	void finish(boolean successful) {
		if (workspace != null) workspace.finish(successful);
	}

	private void launchGeneration() {
		InetSocketAddress address = new InetSocketAddress(context.getBindAddress(), context.getPort());
		current = declaration instanceof MinecraftServer
				? new ManagedServer(declaration.getName(), address, context.getWorkDirectory())
				: new ManagedProxy(declaration.getName(), address, context.getWorkDirectory());
		AgentEndpoint endpoint = provider.platformAgent() == null ? null : agentEndpoint();
		Map<String, String> environment = endpoint == null ? Map.of()
				: Map.of("ANVIL_AGENT_PORT", Integer.toString(endpoint.port()), "ANVIL_AGENT_TOKEN", endpoint.token());

		current.start(command, environment, provider.readinessPattern(), provider.stopCommand(), context.getScenario().getStartupTimeout());
		if (endpoint != null)
			agent.replace(agentConnections.connect(endpoint.port(), endpoint.token(), Duration.ofSeconds(20)));
	}

	private AgentEndpoint agentEndpoint() {
		byte[] token = new byte[32];
		new SecureRandom().nextBytes(token);

		return new AgentEndpoint(ports.select("127.0.0.1"), HexFormat.of().formatHex(token));
	}

	private String cacheIdentity() {
		Distribution distribution = declaration.getDistribution();
		return declaration.getName() + "|" + declaration.getPlatform() + "|"
				+ Objects.toString(distribution.getVersion(), "") + "|" + Objects.toString(distribution.getBuild(), "") + "|"
				+ Objects.toString(distribution.getSha256(), "") + "|" + Objects.toString(distribution.getLocalJar(), "");
	}

	private record AgentEndpoint(int port, String token) { }
}
